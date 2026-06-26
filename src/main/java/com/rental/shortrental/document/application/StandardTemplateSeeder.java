package com.rental.shortrental.document.application;

import com.rental.shortrental.document.entity.DocumentTemplate;
import com.rental.shortrental.document.interfaces.DocumentTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StandardTemplateSeeder implements CommandLineRunner {

    private final DocumentTemplateRepository repository;

    public StandardTemplateSeeder(DocumentTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.findBySystemTrue().isEmpty()) {
            seedContract();
            seedAct();
        }
    }

    private void seedContract() {
        DocumentTemplate tpl = new DocumentTemplate();
        tpl.setLandlord(null);
        tpl.setTitle("Договор аренды");
        tpl.setDocumentType("CONTRACT");
        tpl.setSystem(true);
        tpl.setEditableFieldsConfig("{\"advancePayment\":{\"enabled\":true,\"defaultValue\":5000},\"additionalTerms\":{\"enabled\":true,\"defaultValue\":\"\"}}");
        tpl.setBodyHtml(getContractHtml());
        repository.save(tpl);
    }

    private void seedAct() {
        DocumentTemplate tpl = new DocumentTemplate();
        tpl.setLandlord(null);
        tpl.setTitle("Акт приёма-передачи");
        tpl.setDocumentType("ACT");
        tpl.setSystem(true);
        tpl.setEditableFieldsConfig("{\"advancePayment\":{\"enabled\":false,\"defaultValue\":0},\"additionalTerms\":{\"enabled\":true,\"defaultValue\":\"\"}}");
        tpl.setBodyHtml(getActHtml());
        repository.save(tpl);
    }

    private String getContractHtml() {
        return """
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"/><style>
body{font-family:'DejaVu Serif',serif;font-size:12pt;line-height:1.5;color:#000;margin:40px}
h1{text-align:center;font-size:16pt;margin-bottom:24px}
h2{font-size:13pt;margin-top:20px;margin-bottom:10px}
p{margin:6px 0}
.field{font-weight:bold}
.section{margin-top:16px}
.signatures{margin-top:40px;display:flex;justify-content:space-between}
.signature-block{width:45%}
.signature-line{margin-top:40px;border-top:1px solid #000;padding-top:6px;font-size:10pt}
</style></head><body>
<h1>ДОГОВОР АРЕНДЫ ЖИЛОГО ПОМЕЩЕНИЯ</h1>
<div class="section">
<p>г. <span class="field" th:text="${propertyAddress}">________</span></p>
<p th:text="'«' + ${#temporals.format(#temporals.createNow(), 'dd')} + '» ' + ${#temporals.format(#temporals.createNow(), 'MMMM yyyy')} + ' г.'">________</p>
</div>
<div class="section">
<p><strong>Арендодатель:</strong> <span class="field" th:text="${landlordName}">________</span></p>
<p><strong>Паспорт арендодателя:</strong> <span class="field" th:text="${landlordPassportSeries}">________</span> <span class="field" th:text="${landlordPassportNumber}">________</span></p>
<p><strong>Выдан:</strong> <span class="field" th:text="${landlordPassportIssuedBy}">________</span></p>
<p><strong>Дата выдачи:</strong> <span class="field" th:text="${landlordPassportIssueDate}">________</span></p>
<p><strong>Адрес регистрации арендодателя:</strong> <span class="field" th:text="${landlordRegistrationAddress}">________</span></p>
<p><strong>Телефон арендодателя:</strong> <span class="field" th:text="${landlordPhone}">________</span></p>
<p><strong>Арендатор:</strong> <span class="field" th:text="${guestName}">________</span></p>
<p><strong>Паспорт арендатора:</strong> <span class="field" th:text="${passportSeries}">________</span> <span class="field" th:text="${passportNumber}">________</span></p>
<p><strong>Выдан:</strong> <span class="field" th:text="${passportIssuedBy}">________</span></p>
<p><strong>Дата выдачи:</strong> <span class="field" th:text="${passportIssueDate}">________</span></p>
<p><strong>Дата рождения:</strong> <span class="field" th:text="${birthDate}">________</span></p>
</div>
<div class="section">
<h2>1. Предмет договора</h2>
<p>Арендодатель передает, а Арендатор принимает во временное владение и пользование жилое помещение по адресу: <span class="field" th:text="${propertyAddress}">________</span> (далее — Помещение).</p>
</div>
<div class="section">
<h2>2. Срок аренды</h2>
<p>Срок аренды: с <span class="field" th:text="${startDate}">________</span> по <span class="field" th:text="${endDate}">________</span>.</p>
</div>
<div class="section">
<h2>3. Арендная плата</h2>
<p>Арендная плата составляет: ________ руб. в сутки.</p>
<p th:if="${advancePayment != null && advancePayment != ''}">Аванс: <span class="field" th:text="${advancePayment}">________</span> руб.</p>
<p th:if="${additionalTerms != null && additionalTerms != ''}">Дополнительные условия: <span class="field" th:text="${additionalTerms}">________</span></p>
</div>
<div class="section">
<h2>4. Подписи сторон</h2>
<div class="signatures">
<div class="signature-block"><p><strong>Арендодатель:</strong></p><p th:text="${landlordName}">________</p><p th:text="${landlordPhone}">________</p><div class="signature-line">_____________ / ___________________</div></div>
<div class="signature-block"><p><strong>Арендатор:</strong></p><p th:text="${guestName}">________</p><div class="signature-line">_____________ / ___________________</div></div>
</div>
</div>
</body></html>""";
    }

    private String getActHtml() {
        return """
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"/><style>
body{font-family:'DejaVu Serif',serif;font-size:12pt;line-height:1.5;color:#000;margin:40px}
h1{text-align:center;font-size:16pt;margin-bottom:24px}
p{margin:6px 0}
.field{font-weight:bold}
.signatures{margin-top:40px;display:flex;justify-content:space-between}
.signature-block{width:45%}
.signature-line{margin-top:40px;border-top:1px solid #000;padding-top:6px;font-size:10pt}
</style></head><body>
<h1>АКТ ПРИЁМА-ПЕРЕДАЧИ ЖИЛОГО ПОМЕЩЕНИЯ</h1>
<div class="section">
<p>г. <span class="field" th:text="${propertyAddress}">________</span></p>
<p th:text="'«' + ${#temporals.format(#temporals.createNow(), 'dd')} + '» ' + ${#temporals.format(#temporals.createNow(), 'MMMM yyyy')} + ' г.'">________</p>
</div>
<div class="section">
<p><strong>Арендодатель:</strong> <span class="field" th:text="${landlordName}">________</span></p>
<p><strong>Арендатор:</strong> <span class="field" th:text="${guestName}">________</span></p>
<p><strong>Паспорт арендатора:</strong> <span class="field" th:text="${passportSeries}">________</span> <span class="field" th:text="${passportNumber}">________</span></p>
</div>
<div class="section">
<p>Настоящий Акт составлен о том, что Арендодатель передал, а Арендатор принял во временное владение и пользование жилое помещение по адресу: <span class="field" th:text="${propertyAddress}">________</span>.</p>
<p><strong>Срок аренды:</strong> с <span class="field" th:text="${startDate}">________</span> по <span class="field" th:text="${endDate}">________</span>.</p>
<p>Помещение передано в надлежащем санитарно-техническом состоянии. Стороны претензий друг к другу не имеют.</p>
</div>
<div class="section">
<p th:if="${additionalTerms != null && additionalTerms != ''}"><strong>Дополнительные отметки:</strong> <span class="field" th:text="${additionalTerms}">________</span></p>
</div>
<div class="section">
<h2>Подписи сторон</h2>
<div class="signatures">
<div class="signature-block"><p><strong>Арендодатель:</strong></p><p th:text="${landlordName}">________</p><div class="signature-line">_____________ / ___________________</div></div>
<div class="signature-block"><p><strong>Арендатор:</strong></p><p th:text="${guestName}">________</p><div class="signature-line">_____________ / ___________________</div></div>
</div>
</div>
</body></html>""";
    }
}
