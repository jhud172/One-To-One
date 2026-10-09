package uk.ac.cf._5.group14.One_To_One.Vault;

import java.util.List;

/** A bounded library page; page numbers exposed to native navigation are one-based. */
public record VaultNotePage(List<VaultNote> notes, int page, int pageCount, long total) { }
