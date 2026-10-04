package com.wfm.dto;

/**
 * Request body for {@code PUT /desks/{deskId}/minimum-rest}, mirroring {@link DayStartRequest}'s
 * one-component shape. {@code Integer}, not {@code int} (D-04, REST-04): an absent or explicitly
 * null value reaches {@code DeskService.setMinimumRest} as the clear-the-setting signal rather
 * than being coerced to {@code 0}, which is a real, distinct, configured value of its own.
 */
public record MinimumRestRequest(Integer minimumRestMinutes) {}
