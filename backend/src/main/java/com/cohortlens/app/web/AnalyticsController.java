package com.cohortlens.app.web;

import com.cohortlens.app.service.ObservationStore;
import com.cohortlens.core.AnalyticsService;
import com.cohortlens.core.AnalyticsService.Filter;
import com.cohortlens.core.AnalyticsService.GapDimension;
import com.cohortlens.core.AnalyticsService.GapPoint;
import com.cohortlens.core.AnalyticsService.Options;
import com.cohortlens.core.AnalyticsService.Overview;
import com.cohortlens.core.AnalyticsService.RiskSummary;
import com.cohortlens.core.AnalyticsService.TrendPoint;
import com.cohortlens.core.EconomicStatus;
import com.cohortlens.core.RiskScorer.RiskAssessment;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final ObservationStore store;
    private final AnalyticsService analytics;

    public AnalyticsController(ObservationStore store, AnalyticsService analytics) {
        this.store = store;
        this.analytics = analytics;
    }

    @GetMapping("/options")
    public Options options() {
        return analytics.options(store.all());
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) String school,
                             @RequestParam(required = false) Integer grade,
                             @RequestParam(required = false) EconomicStatus economicStatus,
                             @RequestParam(required = false) Boolean englishLearner) {
        return analytics.overview(store.all(), filter(school, grade, economicStatus, englishLearner));
    }

    @GetMapping("/trend")
    public List<TrendPoint> trend(@RequestParam(required = false) String school,
                                  @RequestParam(required = false) Integer grade,
                                  @RequestParam(required = false) EconomicStatus economicStatus,
                                  @RequestParam(required = false) Boolean englishLearner) {
        return analytics.trend(store.all(), filter(school, grade, economicStatus, englishLearner));
    }

    @GetMapping("/gaps")
    public List<GapPoint> gaps(@RequestParam(defaultValue = "ECONOMIC_STATUS") GapDimension dimension,
                               @RequestParam(required = false) String school,
                               @RequestParam(required = false) Integer grade,
                               @RequestParam(required = false) EconomicStatus economicStatus,
                               @RequestParam(required = false) Boolean englishLearner) {
        return analytics.gaps(store.all(), filter(school, grade, economicStatus, englishLearner), dimension);
    }

    @GetMapping("/risk/summary")
    public RiskSummary riskSummary(@RequestParam(required = false) String school,
                                   @RequestParam(required = false) Integer grade,
                                   @RequestParam(required = false) EconomicStatus economicStatus,
                                   @RequestParam(required = false) Boolean englishLearner) {
        return analytics.riskSummary(store.all(), filter(school, grade, economicStatus, englishLearner));
    }

    /** Individual level (pseudonymized) list. Restricted to RESEARCHER and ADMIN in SecurityConfig. */
    @GetMapping("/risk/students")
    public List<RiskAssessment> riskStudents(@RequestParam(required = false) String school,
                                             @RequestParam(required = false) Integer grade,
                                             @RequestParam(required = false) EconomicStatus economicStatus,
                                             @RequestParam(required = false) Boolean englishLearner,
                                             @RequestParam(defaultValue = "50") int limit) {
        List<RiskAssessment> all = analytics.riskStudents(store.all(),
                filter(school, grade, economicStatus, englishLearner));
        return all.subList(0, Math.max(0, Math.min(limit, Math.min(all.size(), 500))));
    }

    private static Filter filter(String school, Integer grade, EconomicStatus status, Boolean englishLearner) {
        String s = school == null || school.isBlank() ? null : school;
        return new Filter(s, grade, status, englishLearner);
    }
}
