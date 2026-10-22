package com.aiscam.antiscam.simulation.domain;

import java.util.List;

public record FundApplication(List<FundPosting> postings, FundsSummary after) {}
