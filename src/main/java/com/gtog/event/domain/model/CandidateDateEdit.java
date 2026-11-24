package com.gtog.event.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record CandidateDateEdit(String id, LocalDate date, LocalTime startTime) {}
