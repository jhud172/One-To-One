document.addEventListener("DOMContentLoaded", () => {
    const search = document.getElementById("clientSearch");
    const clear = document.getElementById("clientSearchClear");
    const empty = document.getElementById("clientSearchEmpty");
    const rows = [...document.querySelectorAll("[data-client-row]")];
    if (!search || !rows.length) {
        return;
    }
    const normalise = (value) => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase().trim();
    const identities = rows.map(row => normalise(row.querySelector(".client-row__identity")?.textContent || ""));
    const filter = () => {
        const query = normalise(search.value);
        let matches = 0;
        rows.forEach((row, index) => {
            row.hidden = !!query && !identities[index].includes(query);
            if (!row.hidden) matches++;
        });
        if (empty) empty.hidden = matches > 0;
        if (clear) clear.hidden = !query;
    };
    search.addEventListener("input", filter);
    clear?.addEventListener("click", () => { search.value = ""; filter(); search.focus(); });
    filter();
});
