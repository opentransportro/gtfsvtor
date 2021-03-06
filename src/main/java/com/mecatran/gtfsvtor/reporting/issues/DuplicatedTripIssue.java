package com.mecatran.gtfsvtor.reporting.issues;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.mecatran.gtfsvtor.dao.CalendarIndex.OverlappingCalendarInfo;
import com.mecatran.gtfsvtor.model.GtfsTrip;
import com.mecatran.gtfsvtor.reporting.IssueFormatter;
import com.mecatran.gtfsvtor.reporting.ReportIssue;
import com.mecatran.gtfsvtor.reporting.ReportIssuePolicy;
import com.mecatran.gtfsvtor.reporting.ReportIssueSeverity;
import com.mecatran.gtfsvtor.reporting.SourceRefWithFields;

@ReportIssuePolicy(severity = ReportIssueSeverity.WARNING, categoryName = "Duplicated trips")
public class DuplicatedTripIssue implements ReportIssue {

	private GtfsTrip trip1, trip2;
	private OverlappingCalendarInfo calendarOverlap;
	private List<SourceRefWithFields> sourceInfos;

	public DuplicatedTripIssue(GtfsTrip trip1, GtfsTrip trip2,
			OverlappingCalendarInfo calendarOverlap) {
		this.trip1 = trip1;
		this.trip2 = trip2;
		this.calendarOverlap = calendarOverlap;
		this.sourceInfos = Arrays.asList(
				new SourceRefWithFields(trip1.getSourceRef(), "trip_id"),
				new SourceRefWithFields(trip2.getSourceRef(), "trip_id"));
		Collections.sort(this.sourceInfos);
	}

	public GtfsTrip getTrip1() {
		return trip1;
	}

	public GtfsTrip getTrip2() {
		return trip2;
	}

	public OverlappingCalendarInfo getCalendarOverlap() {
		return calendarOverlap;
	}

	@Override
	public List<SourceRefWithFields> getSourceRefs() {
		return sourceInfos;
	}

	@Override
	public void format(IssueFormatter fmt) {
		fmt.text("Duplicated trips on {0} overlapping days, from {1} to {2}",
				fmt.var(Integer.toString(calendarOverlap.getDaysCount())),
				fmt.var(fmt.date(calendarOverlap.getFrom())),
				fmt.var(fmt.date(calendarOverlap.getTo())));
	}
}
