/**
 * Shared fetch helper for landlord pages (session cookie).
 */
async function landlordApi(path, method, body) {
    const isFormData = body instanceof FormData;
    const headers = isFormData ? {} : { "Content-Type": "application/json" };
    const response = await fetch(path, {
        method,
        credentials: "same-origin",
        headers,
        body: body ? (isFormData ? body : JSON.stringify(body)) : undefined
    });
    if (response.status === 401) {
        window.location.href = "/login";
        return null;
    }
    if (!response.ok) {
        const text = await response.text();
        try {
            const j = JSON.parse(text);
            const msg = j.detail || j.error || j.message || (typeof j.title === "string" ? j.title : null);
            if (msg) {
                throw new Error(msg);
            }
        } catch (e) {
            if (e instanceof Error && !(e instanceof SyntaxError)) {
                throw e;
            }
        }
        throw new Error(text || "Request failed");
    }
    if (response.status === 204) return null;
    const ct = response.headers.get("content-type") || "";
    if (ct.includes("application/json")) {
        return response.json();
    }
    return response.text();
}

function setLandlordStatus(elId, message, ok = true) {
    const el = document.getElementById(elId);
    if (!el) return;
    el.textContent = message;
    el.className = "status " + (ok ? "ok" : "err");
}

function formDataToObject(form) {
    const data = Object.fromEntries(new FormData(form).entries());
    const numericKeys = new Set(["propertyId", "guestId", "ownerId"]);
    Object.keys(data).forEach(k => {
        if (numericKeys.has(k) && data[k] !== "") {
            data[k] = Number(data[k]);
        }
    });
    if (data.guestId === "" || data.guestId === null) {
        delete data.guestId;
    }
    return data;
}

function fillPropertySelects(properties) {
    document.querySelectorAll("select[data-property-select]").forEach(sel => {
        const cur = sel.value;
        sel.innerHTML = '<option value="">— Выберите объект —</option>' +
            properties.map(p => `<option value="${p.id}">#${p.id} · ${escapeHtml(p.name)}</option>`).join("");
        if (cur && [...sel.options].some(o => o.value === cur)) {
            sel.value = cur;
        }
    });
}

function escapeHtml(s) {
    if (!s) return "";
    const d = document.createElement("div");
    d.textContent = s;
    return d.innerHTML;
}
