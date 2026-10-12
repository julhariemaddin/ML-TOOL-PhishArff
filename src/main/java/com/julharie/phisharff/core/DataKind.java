package com.julharie.phisharff.core;

import java.util.List;

/**
 * The two kinds of CSV PhishArff understands, and the columns each one needs.
 * Columns are found by NAME (case-insensitive, spaces and hyphens count as underscores).
 * A column can have alternative names (aliases), for example "url", "link" or "website".
 */
public enum DataKind {

    LINK("Link", "links",
            new ColumnSpec("url", true, "url", "link", "urls", "links", "website", "web_address", "address"),
            new ColumnSpec("label", true, "label", "class", "target", "result", "category")),

    EMAIL("Email", "emails",
            new ColumnSpec("body", true, "body", "text", "message", "content", "email_body", "email_text", "mail_body", "email_content"),
            new ColumnSpec("label", true, "label", "class", "target", "result", "category"),
            new ColumnSpec("subject", false, "subject", "email_subject", "title"),
            new ColumnSpec("sender", false, "sender", "from", "email_from", "sender_email", "from_address", "sender_address"));

    /** A column the CSV should have. required = false means the column is optional (conditional feature group). */
    public record ColumnSpec(String name, boolean required, List<String> aliases) {
        ColumnSpec(String name, boolean required, String... aliases) { this(name, required, List.of(aliases)); }
    }

    private final String title;
    private final String plural;
    private final List<ColumnSpec> columns;

    DataKind(String title, String plural, ColumnSpec... columns) {
        this.title = title;
        this.plural = plural;
        this.columns = List.of(columns);
    }

    public String title() { return title; }
    public String plural() { return plural; }
    public List<ColumnSpec> columns() { return columns; }

    /** "url, label" - used in hints. */
    public String requiredNames() {
        StringBuilder sb = new StringBuilder();
        for (ColumnSpec c : columns) {
            if (!c.required()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.name());
        }
        return sb.toString();
    }
}
