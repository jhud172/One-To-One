document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("notes-app");
    if (!root || !window.Quill || root.dataset.notesInitialised) return;
    const byId = id => document.getElementById(id);
    const folderList = byId("folderList"), noteList = byId("noteList"), title = byId("noteTitle");
    const status = byId("saveStatus"), noteSearch = byId("noteSearch"), meta = byId("noteMeta");
    if (!folderList || !noteList || !title || !status) return;
    root.dataset.notesInitialised = "true";
    const copy = key => root.dataset[key] || "";
    const quill = new window.Quill("#editor", { theme: "snow", modules: { toolbar: [
        [{ header: [1, 2, 3, false] }], ["bold", "italic", "underline"],
        [{ list: "ordered" }, { list: "bullet" }], ["link", "blockquote", "code-block"], ["clean"]
    ] } });
    if (byId("notesRichEditor")) byId("notesRichEditor").hidden = false;
    if (byId("notesRichFeedback")) byId("notesRichFeedback").hidden = false;
    if (byId("notesRichFallback")) byId("notesRichFallback").hidden = true;
    quill.root.setAttribute?.("role", "textbox");
    quill.root.setAttribute?.("aria-multiline", "true");
    quill.root.setAttribute?.("aria-label", copy("content"));
    quill.root.setAttribute?.("dir", "auto");
    const toolbar = root.querySelector?.(".ql-toolbar");
    toolbar?.setAttribute("role", "toolbar");
    toolbar?.setAttribute("aria-label", copy("toolbar"));
    const tools = {bold: "bold", italic: "italic", underline: "underline", link: "link",
        blockquote: "quote", "code-block": "code", clean: "clean"};
    toolbar?.querySelectorAll("button").forEach(button => {
        const key = button.classList.contains("ql-list") ? (button.value === "ordered" ? "numbered" : "bullets")
            : Object.keys(tools).find(name => button.classList.contains(`ql-${name}`));
        const label = copy(tools[key] || key);
        button.setAttribute("aria-label", label); button.title = label;
    });
    toolbar?.querySelector(".ql-picker-label")?.setAttribute("aria-label", copy("heading"));
    const headingKeys = {"1": "headingOne", "2": "headingTwo", "3": "headingThree"};
    toolbar?.querySelectorAll(".ql-picker-item").forEach(item => {
        const label = copy(headingKeys[item.dataset.value] || "normal");
        item.dataset.label = label; item.setAttribute("aria-label", label); item.setAttribute("role", "button");
    });
    const headingLabel = toolbar?.querySelector(".ql-picker-label");
    if (headingLabel) headingLabel.dataset.label = copy(headingKeys[headingLabel.dataset.value] || "normal");
    let activeFolder = root.dataset.activeFolder || "", activeNote = root.dataset.activeNote || "";
    let colour = root.dataset.activeColour || "slate", dirty = false, saving = null, switching = false;
    let savedRevision = root.dataset.activeRevision || "", conflict = false;
    let currentPage = Number(root.dataset.activePage || 1);
    const wide = window.matchMedia?.("(min-width: 1250px)");
    for (const id of ["notesCollections", "notesListCollection"]) {
        const details = byId(id);
        if (!details || !wide) continue;
        let chosen = false;
        const adapt = () => { if (!chosen) details.open = wide.matches || (id === "notesListCollection" && !activeNote); };
        adapt(); wide.addEventListener("change", adapt);
        details.querySelector("summary")?.addEventListener("click", () => { chosen = true; });
    }
    let saveTimer = null, searchTimer = null, searchRevision = 0;
    const showStatus = (key, failed = false) => {
        status.textContent = copy(key); status.classList.toggle("notes-save-error", failed);
    };
    const editable = value => {
        title.readOnly = !value; quill.enable(value);
        toolbar?.querySelectorAll("button").forEach(button => { button.disabled = !value; });
        toolbar?.querySelector(".ql-picker-label")?.setAttribute("aria-disabled", String(!value));
        if (byId("deleteNoteBtn")) byId("deleteNoteBtn").disabled = !activeNote || !value;
    };
    const updateActions = () => {
        if (byId("addNoteBtn")) byId("addNoteBtn").href = activeFolder ? `/notes/folders/${activeFolder}/new` : "/notes";
        if (byId("notesFolderManage")) byId("notesFolderManage").href = activeFolder ? `/notes/folders/${activeFolder}` : "/notes";
        if (byId("notesDeleteForm")) byId("notesDeleteForm").action = activeNote ? `/notes/${activeNote}/delete` : "/notes";
        if (byId("exportHtmlBtn")) byId("exportHtmlBtn").href = activeNote ? `/notes/export/${activeNote}?format=html` : "/notes";
        if (byId("notesNativeEdit")) byId("notesNativeEdit").href = activeNote ? `/notes/${activeNote}/edit` : "/notes";
        for (const id of ["exportHtmlBtn", "notesNativeEdit"]) if (byId(id)) byId(id).hidden = !activeNote;
        if (byId("notesSearchFolder")) byId("notesSearchFolder").value = activeFolder;
        const params = new URLSearchParams({folderId: activeFolder, q: noteSearch?.value || "", page: currentPage});
        if (activeNote) params.set("noteId", activeNote);
        if (activeFolder) window.history?.replaceState(null, "", `/notes?${params}`);
    };
    const confirmedNote = (note, id) => {
        if (!note || !Number.isSafeInteger(Number(note.id)) || Number(note.id) < 1
                || (id != null && String(note.id) !== String(id))
                || typeof note.revision !== "string" || !/^[a-f0-9]{64}$/.test(note.revision)) {
            throw new Error("notes-acknowledgement");
        }
        return note;
    };
    if (byId("notes-active-content")?.value) quill.clipboard.dangerouslyPasteHTML(byId("notes-active-content").value);
    editable(Boolean(activeNote)); updateActions();
    const request = async (url, options = {}) => {
        const headers = { "Content-Type": "application/json", ...options.headers };
        if (root.dataset.csrfToken) headers[root.dataset.csrfHeader || "X-CSRF-TOKEN"] = root.dataset.csrfToken;
        const response = await fetch(url, { ...options, headers });
        if (!response.ok || (response.status !== 204 && !response.headers.get("content-type")?.includes("application/json"))) {
            const error = new Error("notes-request"); error.status = response.status; throw error;
        }
        return response.status === 204 ? null : response.json();
    };
    const link = (label, href) => {
        const anchor = document.createElement("a"); anchor.href = href; anchor.textContent = label;
        return anchor;
    };
    const renderFolders = folders => {
        folderList.replaceChildren();
        folders.forEach(folder => {
            const anchor = link(folder.name || "", `/notes?folderId=${encodeURIComponent(folder.id)}`);
            anchor.className = "folder-item"; anchor.dataset.folderId = folder.id;
            anchor.setAttribute("aria-current", String(folder.id) === String(activeFolder) ? "page" : "false");
            anchor.addEventListener("click", event => { event.preventDefault(); selectFolder(folder.id); });
            folderList.appendChild(anchor);
        });
    };
    const renderNotes = notes => {
        noteList.replaceChildren();
        if (!notes.length) {
            const empty = document.createElement("p"); empty.className = "notes-empty";
            empty.textContent = copy("empty"); noteList.appendChild(empty); return;
        }
        notes.forEach(note => {
            const anchor = link("", `/notes/${encodeURIComponent(note.id)}`);
            anchor.className = "note-item"; anchor.dataset.noteId = note.id;
            anchor.setAttribute("aria-current", String(note.id) === String(activeNote) ? "page" : "false");
            const heading = document.createElement("strong"); heading.textContent = note.title || copy("untitled"); heading.dir = "auto";
            const time = document.createElement("time"); time.textContent = note.updatedAt ? new Date(note.updatedAt).toLocaleDateString(document.documentElement?.lang || undefined) : "";
            if (note.updatedAt) time.dateTime = note.updatedAt;
            const preview = document.createElement("p"); preview.textContent = note.preview || "";
            anchor.appendChild(heading); anchor.appendChild(time); anchor.appendChild(preview);
            anchor.addEventListener("click", event => { event.preventDefault(); loadNote(note.id); });
            noteList.appendChild(anchor);
        });
    };
    const loadFolders = async () => {
        try { renderFolders(await request("/notes/api/folders")); }
        catch { showStatus("failed", true); }
    };
    const renderPage = result => {
        if (!result || !Array.isArray(result.notes) || !Number.isSafeInteger(result.page) || result.page < 1
                || !Number.isSafeInteger(result.pageCount) || result.pageCount < result.page
                || !Number.isSafeInteger(result.total) || result.total < 0) throw new Error("notes-page");
        currentPage = result.page; renderNotes(result.notes);
        const pager = byId("notesPagination");
        if (!pager) return;
        pager.replaceChildren();
        const status = document.createElement("p");
        status.textContent = copy("pageLabel").replace("{0}", result.page).replace("{1}", result.pageCount).replace("{2}", result.total);
        pager.appendChild(status);
        const params = new URLSearchParams({folderId: activeFolder, q: noteSearch?.value || ""});
        for (const [page, key] of [[result.page - 1, "previous"], [result.page + 1, "next"]]) {
            if (page < 1 || page > result.pageCount) continue;
            params.set("page", page);
            const anchor = link(copy(key), `/notes?${params}`);
            anchor.className = "notes-action"; anchor.dataset.notesPage = page; pager.appendChild(anchor);
        }
    };
    const loadNotes = async () => {
        const revision = ++searchRevision, folder = activeFolder;
        if (!folder) { renderNotes([]); return; }
        try {
            const params = new URLSearchParams({ folderId: folder, q: noteSearch?.value || "", page: currentPage });
            const notes = await request(`/notes/api/notes/page?${params}`);
            if (revision === searchRevision && String(folder) === String(activeFolder)) { renderPage(notes); updateActions(); }
        } catch { if (revision === searchRevision) showStatus("failed", true); }
    };
    const snapshot = () => ({ title: title.value.trim() || copy("untitled"), content: quill.root.innerHTML,
        folderId: activeFolder, colour });
    const saveNote = async () => {
        clearTimeout(saveTimer);
        if (saving) return saving;
        if (conflict) return false;
        if (!dirty || !activeNote) return true;
        saving = (async () => {
            while (dirty) {
                const id = activeNote, payload = snapshot();
                showStatus("saving");
                try {
                    const note = confirmedNote(await request(`/notes/api/notes/${id}`, { method: "POST",
                        body: JSON.stringify({...payload, revision: savedRevision}) }), id);
                    if (String(id) !== String(activeNote)) return false;
                    if (meta) meta.textContent = note.updatedAt ? new Date(note.updatedAt).toLocaleString(document.documentElement?.lang || undefined) : "";
                    savedRevision = note.revision;
                    dirty = JSON.stringify(payload) !== JSON.stringify(snapshot());
                    if (!dirty) showStatus("saved");
                } catch (error) {
                    dirty = true; conflict = error.status === 409;
                    showStatus(conflict ? "conflict" : "failed", true);
                    if (byId("notesConflict")) byId("notesConflict").hidden = !conflict;
                    return false;
                }
            }
            return true;
        })();
        const result = await saving; saving = null;
        if (result) await loadNotes();
        return result;
    };
    const edited = () => {
        if (!activeNote || switching) return;
        dirty = true; showStatus(conflict ? "conflict" : "unsaved", conflict); clearTimeout(saveTimer);
        saveTimer = setTimeout(saveNote, 1000);
    };
    const loadNote = async id => {
        if (switching || !(await saveNote()) || switching) return;
        switching = true; editable(false);
        try {
            const note = confirmedNote(await request(`/notes/api/notes/${id}`), id);
            activeNote = note.id; activeFolder = note.folderId; colour = note.colour || "slate";
            savedRevision = note.revision; conflict = false;
            title.value = note.title || "";
            quill.setContents([], "silent"); quill.clipboard.dangerouslyPasteHTML(note.content || "", "silent");
            dirty = false; if (meta) meta.textContent = note.updatedAt ? new Date(note.updatedAt).toLocaleString(document.documentElement?.lang || undefined) : "";
            updateActions(); showStatus("saved"); await loadNotes(); await loadFolders();
            if (wide && !wide.matches && byId("notesListCollection")) {
                byId("notesListCollection").open = false; title.focus();
            }
        } catch { showStatus("failed", true); }
        finally { switching = false; editable(Boolean(activeNote)); }
    };
    const selectFolder = async id => {
        if (switching || !(await saveNote()) || switching) return;
        switching = true; editable(false);
        try {
            const notes = await request(`/notes/api/notes/page?${new URLSearchParams({ folderId: id, q: noteSearch?.value || "", page: 1 })}`);
            activeFolder = id; activeNote = ""; colour = "slate"; dirty = false;
            savedRevision = "";
            title.value = ""; if (meta) meta.textContent = ""; quill.setContents([], "silent");
            renderPage(notes); updateActions(); await loadFolders();
            if (byId("notesListCollection")) byId("notesListCollection").open = true;
        } catch { showStatus("failed", true); }
        finally { switching = false; editable(Boolean(activeNote)); }
    };
    byId("addNoteBtn")?.addEventListener("click", async event => {
        event.preventDefault(); if (switching || !activeFolder || !(await saveNote()) || switching) return;
        switching = true; editable(false);
        try {
            const note = confirmedNote(await request("/notes/api/notes", { method: "POST", body: JSON.stringify({
                folderId: activeFolder, title: copy("untitled"), content: "", colour: "slate"
            }) }));
            currentPage = 1;
            switching = false;
            await loadNote(note.id);
        } catch { showStatus("failed", true); }
        finally { switching = false; editable(Boolean(activeNote)); }
    });
    byId("notesFolderCreateForm")?.addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const name = byId("newFolderName").value.trim();
            await request("/notes/api/folders", { method: "POST", body: JSON.stringify({ name, colour: "slate" }) });
            byId("newFolderName").value = ""; byId("newFolderRow").open = false; await loadFolders();
        } catch { showStatus("failed", true); }
    });
    title.addEventListener("input", edited);
    quill.on("text-change", (_delta, _old, source) => { if (source === "user") edited(); });
    noteSearch?.addEventListener("input", () => {
        currentPage = 1;
        clearTimeout(searchTimer); searchTimer = setTimeout(loadNotes, 300);
    });
    byId("folderSearch")?.addEventListener("input", event => {
        const query = event.target.value.toLocaleLowerCase();
        Array.from(folderList.children).forEach(item => { item.hidden = !item.textContent.toLocaleLowerCase().includes(query); });
    });
    byId("notesRetry")?.addEventListener("click", async () => {
        if (dirty) await saveNote(); else { await loadFolders(); await loadNotes(); }
    });
    byId("notesSearchForm")?.addEventListener("submit", event => { event.preventDefault(); currentPage = 1; loadNotes(); });
    byId("notesPagination")?.addEventListener("click", async event => {
        const anchor = event.target.closest("[data-notes-page]");
        if (!anchor) return;
        event.preventDefault();
        if (switching || !(await saveNote())) return;
        currentPage = Number(anchor.dataset.notesPage); await loadNotes();
    });
    byId("notesReviewSaved")?.addEventListener("click", async () => {
        if (!conflict || !activeNote) return;
        try {
            const current = confirmedNote(await request(`/notes/api/notes/${activeNote}`), activeNote);
            byId("notesSavedTitle").textContent = current.title || copy("untitled");
            byId("notesSavedContent").value = current.plainContent || "";
            byId("notesKeepDraft").hidden = false;
            byId("notesKeepDraft").dataset.reviewedRevision = current.revision;
        } catch { showStatus("failed", true); }
    });
    byId("notesKeepDraft")?.addEventListener("click", async () => {
        const reviewed = byId("notesKeepDraft").dataset.reviewedRevision;
        if (!conflict || !reviewed) return;
        savedRevision = reviewed; conflict = false;
        byId("notesConflict").hidden = true; byId("notesKeepDraft").hidden = true;
        delete byId("notesKeepDraft").dataset.reviewedRevision;
        await saveNote();
    });
    for (const id of ["exportHtmlBtn", "notesNativeEdit"]) {
        byId(id)?.addEventListener("click", async event => {
            if (!dirty && !saving) return;
            event.preventDefault();
            if (await saveNote()) window.location.assign(byId(id).href);
        });
    }
    byId("notesDeleteForm")?.addEventListener("submit", event => {
        if (event.defaultPrevented) return;
        // The shared confirmation runs in capture phase; only an approved submission reaches here.
        clearTimeout(saveTimer); dirty = false;
    });
    window.addEventListener("beforeunload", event => {
        if (dirty || saving) { event.preventDefault(); event.returnValue = ""; }
    });
    root.querySelectorAll("[data-folder-id]").forEach(anchor => anchor.addEventListener("click", event => {
        event.preventDefault(); selectFolder(anchor.dataset.folderId);
    }));
    root.querySelectorAll("[data-note-id]").forEach(anchor => anchor.addEventListener("click", event => {
        event.preventDefault(); loadNote(anchor.dataset.noteId);
    }));
    loadFolders(); loadNotes();
});
