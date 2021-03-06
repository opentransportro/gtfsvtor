package com.mecatran.gtfsvtor.reporting.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mecatran.gtfsvtor.cmdline.GtfsVtorMain;
import com.mecatran.gtfsvtor.cmdline.ManifestReader;
import com.mecatran.gtfsvtor.lib.GtfsVtorOptions.NamedDataIO;
import com.mecatran.gtfsvtor.reporting.ReportFormatter;
import com.mecatran.gtfsvtor.reporting.ReportIssueSeverity;
import com.mecatran.gtfsvtor.reporting.ReviewReport;
import com.mecatran.gtfsvtor.reporting.ReviewReport.IssueCount;
import com.mecatran.gtfsvtor.reporting.json.model.JsonReport;
import com.mecatran.gtfsvtor.reporting.json.model.JsonReport.JsonInputDataInfo;
import com.mecatran.gtfsvtor.reporting.json.model.JsonReport.JsonValidationRun;
import com.mecatran.gtfsvtor.reporting.json.model.JsonReport.JsonValidatorInfo;
import com.mecatran.gtfsvtor.utils.SystemEnvironment;

public class JsonReportFormatter implements ReportFormatter {

	private NamedDataIO dataIO;
	private String inputFilename;

	public JsonReportFormatter(NamedDataIO dataIO) {
		this.dataIO = dataIO;
	}

	public JsonReportFormatter withInputFileName(String filename) {
		this.inputFilename = filename;
		return this;
	}

	@Override
	public void format(ReviewReport report) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		JsonReport jreport;
		Optional<InputStream> inopt = dataIO.getInputStream();
		if (inopt.isPresent()) {
			try {
				/*
				 * TODO Please note that we reload everything into memory. Is
				 * there a way to just append json data w/o reading the whole
				 * thing? We do not care about the previous data.
				 */
				System.out.println("Reading previous JSON report from "
						+ dataIO.getName());
				// Read previous values in append mode
				jreport = mapper.readValue(inopt.get(), JsonReport.class);
			} catch (IOException e) {
				System.err.println("Cannot read previous report "
						+ dataIO.getName() + ": " + e);
				// Ignore, fallback
				jreport = new JsonReport();
			}
		} else {
			jreport = new JsonReport();
		}
		JsonValidationRun run = convert(report);
		jreport.reports.add(run);
		OutputStream out = dataIO.getOutputStream();
		mapper.writeValue(out, jreport);
		out.close();
		System.out.println("JSON report output to " + dataIO.getName());
	}

	private JsonValidationRun convert(ReviewReport report) {

		JsonValidationRun run = new JsonValidationRun();
		run.timestamp = SystemEnvironment.now();

		run.validator = new JsonValidatorInfo();
		ManifestReader mfr = new ManifestReader(GtfsVtorMain.class);
		run.validator.name = "GTFSVTOR";
		run.validator.version = mfr.getApplicationVersion();
		run.validator.buildDate = mfr.getApplicationBuildDate();
		run.validator.buildRev = mfr.getApplicationBuildRevision();
		run.validator.copyrights = "Copyright (c) Mecatran";

		run.input = new JsonInputDataInfo();
		run.input.filename = inputFilename;

		run.summary = new JsonReport.JsonSummary();
		run.summary.severities = Arrays.stream(ReportIssueSeverity.values())
				.map(severity -> {
					IssueCount count = report.issuesCountOfSeverity(severity);
					JsonReport.JsonSeverityCount jcount = new JsonReport.JsonSeverityCount();
					jcount.severity = severity.toString();
					jcount.totalCount = count.totalCount();
					// TODO use 0 for summary, reportedCount() for a whole
					// report
					jcount.reportedCount = 0;
					return jcount;
				}).collect(Collectors.toList());
		run.summary.categories = report.getCategories().sorted()
				.map(category -> {
					IssueCount count = report.issuesCountOfCategory(category);
					JsonReport.JsonCategoryCount jcount = new JsonReport.JsonCategoryCount();
					jcount.severity = category.getSeverity().toString();
					jcount.categoryName = category.getCategoryName();
					jcount.totalCount = count.totalCount();
					// TODO use 0 for summary, reportedCount() for a whole
					// report
					jcount.reportedCount = 0;
					return jcount;
				}).collect(Collectors.toList());
		return run;
	}
}
