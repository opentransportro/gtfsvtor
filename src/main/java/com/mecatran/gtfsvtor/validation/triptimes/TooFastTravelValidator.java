package com.mecatran.gtfsvtor.validation.triptimes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mecatran.gtfsvtor.dao.IndexedReadOnlyDao;
import com.mecatran.gtfsvtor.dao.LinearGeometryIndex;
import com.mecatran.gtfsvtor.dao.LinearGeometryIndex.ProjectedPoint;
import com.mecatran.gtfsvtor.model.GtfsLogicalTime;
import com.mecatran.gtfsvtor.model.GtfsRoute;
import com.mecatran.gtfsvtor.model.GtfsRouteType;
import com.mecatran.gtfsvtor.model.GtfsStop;
import com.mecatran.gtfsvtor.model.GtfsStopTime;
import com.mecatran.gtfsvtor.model.GtfsTrip;
import com.mecatran.gtfsvtor.model.GtfsTripAndTimes;
import com.mecatran.gtfsvtor.reporting.ReportIssueSeverity;
import com.mecatran.gtfsvtor.reporting.ReportSink;
import com.mecatran.gtfsvtor.reporting.issues.TimeTravelError;
import com.mecatran.gtfsvtor.reporting.issues.TooFastTravelIssue;
import com.mecatran.gtfsvtor.reporting.issues.TooManyStopWithSameTimeIssue;
import com.mecatran.gtfsvtor.validation.ConfigurableOption;
import com.mecatran.gtfsvtor.validation.DaoValidator.Context;
import com.mecatran.gtfsvtor.validation.TripTimesValidator;
import com.mecatran.gtfsvtor.validation.ValidatorConfig;

public class TooFastTravelValidator implements TripTimesValidator {

	// TODO Each configurable speed are not described right now

	@ConfigurableOption(description = "Speed multiplier factor above which an error is generated instead of a warning")
	private double errorSpeedMultiplier = 3.;

	@ConfigurableOption(description = "Number of trip stop with identical time above which a warning is generated")
	private int maxStopsWithSameTime = 5;

	@Override
	public void validate(Context context, GtfsTripAndTimes tripAndTimes) {
		IndexedReadOnlyDao dao = context.getDao();
		LinearGeometryIndex lgi = dao.getLinearGeometryIndex();
		ReportSink reportSink = context.getReportSink();
		GtfsTrip trip = tripAndTimes.getTrip();
		List<GtfsStopTime> stopTimes = tripAndTimes.getStopTimes();
		GtfsRoute route = dao.getRoute(trip.getRouteId());

		double maxSpeedMps = getMaxSpeedMps(route, context.getConfig());
		boolean hasExactSeconds = false;
		for (GtfsStopTime stopTime : stopTimes) {
			if (stopTime.getArrivalTime() != null
					&& stopTime.getArrivalTime().getSecond() != 0) {
				hasExactSeconds = true;
				break;
			}
			if (stopTime.getDepartureTime() != null
					&& stopTime.getDepartureTime().getSecond() != 0) {
				hasExactSeconds = true;
				break;
			}
		}
		int slackSec = hasExactSeconds ? 0 : 60;
		GtfsStopTime lastValidStopTime = null;
		ProjectedPoint lastValidProjectedPoint = null;
		int sameTimeCounter = 1;
		for (GtfsStopTime stopTime : stopTimes) {
			ProjectedPoint projectedPoint = lgi.getProjectedPoint(stopTime);
			GtfsLogicalTime arrivalTime = stopTime.getArrivalOrDepartureTime();
			if (projectedPoint != null && arrivalTime != null) {
				if (lastValidStopTime != null) {
					double d = projectedPoint.getArcLengthMeters()
							- lastValidProjectedPoint.getArcLengthMeters();
					int t = arrivalTime.getSecondSinceMidnight()
							- lastValidStopTime.getDepartureOrArrivalTime()
									.getSecondSinceMidnight();
					if (t < 0) {
						// Time-travel
						GtfsStop stop1 = dao
								.getStop(lastValidStopTime.getStopId());
						GtfsStop stop2 = dao.getStop(stopTime.getStopId());
						reportSink.report(new TimeTravelError(route, trip,
								lastValidStopTime, stop1, stopTime, stop2));
					} else {
						// Forward-time
						if (t == 0) {
							sameTimeCounter++;
						} else {
							if (sameTimeCounter > maxStopsWithSameTime) {
								reportSink.report(
										new TooManyStopWithSameTimeIssue(route,
												trip, arrivalTime,
												sameTimeCounter));
							}
							sameTimeCounter = 1;
						}
						double speedMps = d / (t + slackSec);
						if (speedMps > maxSpeedMps) {
							// Too fast travel
							GtfsStop stop1 = dao
									.getStop(lastValidStopTime.getStopId());
							GtfsStop stop2 = dao.getStop(stopTime.getStopId());
							ReportIssueSeverity severity = getSeverity(speedMps,
									maxSpeedMps);
							reportSink.report(new TooFastTravelIssue(route,
									trip, lastValidStopTime, stop1, stopTime,
									stop2, d, speedMps, maxSpeedMps, severity));
						}
					}
				}
				lastValidStopTime = stopTime;
				lastValidProjectedPoint = projectedPoint;
			}
		}
		if (sameTimeCounter > maxStopsWithSameTime) {
			reportSink.report(new TooManyStopWithSameTimeIssue(route, trip,
					lastValidStopTime.getArrivalTime(), sameTimeCounter));
		}
	}

	private ReportIssueSeverity getSeverity(double speedMps,
			double maxSpeedMps) {
		double factor = speedMps / maxSpeedMps;
		return factor >= errorSpeedMultiplier ? ReportIssueSeverity.ERROR
				: ReportIssueSeverity.WARNING;
	}

	private Map<Integer, Double> maxSpeedsCache = new HashMap<>();

	private double getMaxSpeedMps(GtfsRoute route, ValidatorConfig config) {
		// For bogus route, test anyway, fallback on BUS
		int routeTypeCode = GtfsRouteType.BUS_CODE;
		if (route != null && route.getType() != null)
			routeTypeCode = route.getType().getValue();
		Double maxSpeedMps = maxSpeedsCache.computeIfAbsent(routeTypeCode,
				code -> getMaxSpeedMps(code, config));
		return maxSpeedMps;
	}

	private double getMaxSpeedMps(int routeTypeCode, ValidatorConfig config) {
		double maxSpeedKph;
		switch (GtfsRouteType.mapExtendedToBaseRouteTypeCode(routeTypeCode)) {
		default:
		case GtfsRouteType.CABLE_CAR_CODE:
		case GtfsRouteType.GONDOLA_CODE:
		case GtfsRouteType.FUNICULAR_CODE:
			maxSpeedKph = 50;
			break;
		case GtfsRouteType.FERRY_CODE:
			maxSpeedKph = 80;
			break;
		case GtfsRouteType.TRAM_CODE:
		case GtfsRouteType.BUS_CODE:
		case GtfsRouteType.TROLLEYBUS_CODE:
				maxSpeedKph = 100;
			break;
	  case GtfsRouteType.INTERCITY_BUS_CODE:
	  	// Note: not yet officially adopted routeType
	  	maxSpeedKph = 120;
			break;
	  case GtfsRouteType.MONORAIL_CODE:
	  case GtfsRouteType.METRO_CODE:
			maxSpeedKph = 150;
			break;
		case GtfsRouteType.RAIL_CODE:
			maxSpeedKph = 300;
			break;
		}
		maxSpeedKph = config.getDouble(
				config.getKey(this, "maxSpeedKph." + routeTypeCode),
				maxSpeedKph);
		return maxSpeedKph / 3.6; // in m/s
	}

}
