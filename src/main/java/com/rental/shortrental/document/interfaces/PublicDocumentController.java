package com.rental.shortrental.document.interfaces;

import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.document.entity.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PublicDocumentController {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    public PublicDocumentController(DocumentRepository documentRepository, FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/api/public/documents/{token}")
    public ResponseEntity<byte[]> servePdf(@PathVariable String token) {
        Document doc = documentRepository.findByPdfToken(token)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Document not found"));
        byte[] data = fileStorageService.load(doc.getPdfFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getTitle() + ".pdf\"")
                .body(data);
    }
}
