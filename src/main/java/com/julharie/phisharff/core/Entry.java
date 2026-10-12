package com.julharie.phisharff.core;

/**
 * One data row from the CSV. Fields that do not apply to the data type are null
 * (a link row has no subject/sender/body; an email row has no url).
 */
public record Entry(int row, String url, String subject, String sender, String body, String label) {}
