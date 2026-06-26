package com.rental.shortrental.document.application;

import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.entity.DocumentTemplate;
import com.rental.shortrental.document.interfaces.DocumentRepository;
import com.rental.shortrental.document.interfaces.DocumentTemplateRepository;
import com.rental.shortrental.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentLinkService {

    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final String publicBaseUrl;

    public DocumentLinkService(DocumentRepository documentRepository,
                               DocumentTemplateRepository documentTemplateRepository,
                               @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.documentRepository = documentRepository;
        this.documentTemplateRepository = documentTemplateRepository;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    @Transactional
    public String createDocumentLink(User landlord, User guest, Long templateId) {
        DocumentTemplate template = resolveTemplate(landlord, templateId);
        if (template == null) {
            return "";
        }

        String token = UUID.randomUUID().toString();

        Document doc = new Document();
        doc.setTitle(template.getTitle());
        doc.setDocumentType(template.getDocumentType());
        doc.setStatus("DRAFT");
        doc.setGuest(guest);
        doc.setLandlord(landlord);
        doc.setTemplate(template);
        doc.setGuestFillToken(token);
        doc.setGuestFillExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        doc.setEditableJson(buildEditableJson(template));
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        return publicBaseUrl + "/guest/data/" + token;
    }

    private DocumentTemplate resolveTemplate(User landlord, Long templateId) {
        if (templateId != null) {
            DocumentTemplate template = documentTemplateRepository.findById(templateId).orElse(null);
            if (template == null) {
                return null;
            }
            if (template.isSystem()) {
                return template;
            }
            if (template.getLandlord() != null && template.getLandlord().getId().equals(landlord.getId())) {
                return template;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Template belongs to another landlord");
        }
        List<DocumentTemplate> templates = documentTemplateRepository.findByLandlordIdOrSystemTrueOrderByTitle(landlord.getId());
        if (!templates.isEmpty()) {
            return templates.get(0);
        }
        return null;
    }

    private String buildEditableJson(DocumentTemplate template) {
        String config = template.getEditableFieldsConfig();
        if (config == null || config.isBlank()) return "{}";
        try {
            Map<String, String> result = new LinkedHashMap<>();

            // Parse advancePayment: {"enabled":true,"defaultValue":5000}
            String advanceBlock = extractJsonBlock(config, "advancePayment");
            if (advanceBlock != null && advanceBlock.contains("\"enabled\":true")) {
                String defVal = extractJsonValue(advanceBlock, "defaultValue");
                if (defVal != null) result.put("advancePayment", defVal);
            }

            // Parse additionalTerms
            String termsBlock = extractJsonBlock(config, "additionalTerms");
            if (termsBlock != null && termsBlock.contains("\"enabled\":true")) {
                String defVal = extractJsonValue(termsBlock, "defaultValue");
                if (defVal != null) result.put("additionalTerms", defVal);
            }

            return mapToJson(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractJsonBlock(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        if (start >= json.length() || json.charAt(start) != '{') return null;
        int depth = 1;
        int i = start + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        return json.substring(start, i);
    }

    private String extractJsonValue(String block, String key) {
        String search = "\"" + key + "\":";
        int start = block.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        while (start < block.length() && (block.charAt(start) == ' ' || block.charAt(start) == '\t')) start++;
        if (start >= block.length()) return null;
        if (block.charAt(start) == '"') {
            int end = start + 1;
            while (end < block.length() && block.charAt(end) != '"') {
                if (block.charAt(end) == '\\') end++;
                end++;
            }
            return block.substring(start + 1, end);
        }
        int end = start;
        while (end < block.length() && block.charAt(end) != ',' && block.charAt(end) != '}' && block.charAt(end) != ' ') end++;
        return block.substring(start, end);
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"");
            sb.append(escapeJson(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
