/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.task.projectanalysis.qualitymodel;

import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.component.TreeRootHolderRule;
import org.sonar.ce.task.projectanalysis.component.VisitorsCrawler;
import org.sonar.ce.task.projectanalysis.issue.ComponentIssuesRepositoryRule;
import org.sonar.ce.task.projectanalysis.issue.FillComponentIssuesVisitorRule;
import org.sonar.ce.task.projectanalysis.measure.Measure;
import org.sonar.ce.task.projectanalysis.measure.MeasureRepositoryRule;
import org.sonar.ce.task.projectanalysis.metric.MetricRepositoryRule;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.util.Uuids;
import org.sonar.server.measure.Rating;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_SAFE;
import static org.sonar.api.measures.CoreMetrics.SECURITY_HOTSPOTS_REVIEWED;
import static org.sonar.api.measures.CoreMetrics.SECURITY_HOTSPOTS_REVIEWED_KEY;
import static org.sonar.api.measures.CoreMetrics.SECURITY_REVIEW_RATING;
import static org.sonar.api.measures.CoreMetrics.SECURITY_REVIEW_RATING_KEY;
import static org.sonar.api.rule.Severity.MAJOR;
import static org.sonar.api.rule.Severity.MINOR;
import static org.sonar.ce.task.projectanalysis.component.Component.Type.DIRECTORY;
import static org.sonar.ce.task.projectanalysis.component.Component.Type.FILE;
import static org.sonar.ce.task.projectanalysis.component.ReportComponent.builder;
import static org.sonar.core.issue.DefaultIssue.STATUS_REVIEWED;
import static org.sonar.core.issue.DefaultIssue.STATUS_TO_REVIEW;
import static org.sonar.server.measure.Rating.A;
import static org.sonar.server.measure.Rating.B;
import static org.sonar.server.measure.Rating.C;
import static org.sonar.server.measure.Rating.D;
import static org.sonar.server.measure.Rating.E;

public class SecurityReviewMeasuresVisitorTest {

  private static final int PROJECT_REF = 1;
  private static final int ROOT_DIR_REF = 12;
  private static final int DIRECTORY_REF = 123;
  private static final int FILE_1_REF = 1231;
  private static final int FILE_2_REF = 1232;

  static final Component ROOT_PROJECT = builder(Component.Type.PROJECT, PROJECT_REF).setKey("project")
    .addChildren(
      builder(DIRECTORY, ROOT_DIR_REF).setKey("dir")
        .addChildren(
          builder(DIRECTORY, DIRECTORY_REF).setKey("directory")
            .addChildren(
              builder(FILE, FILE_1_REF).setKey("file1").build(),
              builder(FILE, FILE_2_REF).setKey("file2").build())
            .build())
        .build())
    .build();

  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule();
  @Rule
  public MetricRepositoryRule metricRepository = new MetricRepositoryRule()
    .add(SECURITY_REVIEW_RATING)
    .add(SECURITY_HOTSPOTS_REVIEWED);
  @Rule
  public ComponentIssuesRepositoryRule componentIssuesRepositoryRule = new ComponentIssuesRepositoryRule(treeRootHolder);
  @Rule
  public FillComponentIssuesVisitorRule fillComponentIssuesVisitorRule = new FillComponentIssuesVisitorRule(componentIssuesRepositoryRule, treeRootHolder);
  @Rule
  public MeasureRepositoryRule measureRepository = MeasureRepositoryRule.create(treeRootHolder, metricRepository);

  private VisitorsCrawler underTest = new VisitorsCrawler(asList(fillComponentIssuesVisitorRule,
    new SecurityReviewMeasuresVisitor(componentIssuesRepositoryRule, measureRepository, metricRepository)));

  @Test
  public void compute_measures_when_100_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_SAFE),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, A, 100.0);
    verifyMeasures(FILE_2_REF, A, 100.0);
    verifyMeasures(DIRECTORY_REF, A, 100.0);
    verifyMeasures(ROOT_DIR_REF, A, 100.0);
    verifyMeasures(PROJECT_REF, A, 100.0);
  }

  @Test
  public void compute_measures_when_more_than_80_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, A, 100.0);
    verifyMeasures(FILE_2_REF, A, 80.0);
    verifyMeasures(DIRECTORY_REF, A, 87.5);
    verifyMeasures(ROOT_DIR_REF, A, 87.5);
    verifyMeasures(PROJECT_REF, A, 87.5);
  }

  @Test
  public void compute_measures_when_more_than_70_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, A, 100.0);
    verifyMeasures(FILE_2_REF, B, 71.4);
    verifyMeasures(DIRECTORY_REF, B, 75.0);
    verifyMeasures(ROOT_DIR_REF, B, 75.0);
    verifyMeasures(PROJECT_REF, B, 75.0);
  }

  @Test
  public void compute_measures_when_more_than_50_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, C, 50.0);
    verifyMeasures(FILE_2_REF, C, 60.0);
    verifyMeasures(DIRECTORY_REF, C, 57.1);
    verifyMeasures(ROOT_DIR_REF, C, 57.1);
    verifyMeasures(PROJECT_REF, C, 57.1);
  }

  @Test
  public void compute_measures_when_more_30_than_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, D, 33.3);
    verifyMeasures(FILE_2_REF, D, 40.0);
    verifyMeasures(DIRECTORY_REF, D, 37.5);
    verifyMeasures(ROOT_DIR_REF, D, 37.5);
    verifyMeasures(PROJECT_REF, D, 37.5);
  }

  @Test
  public void compute_measures_when_less_than_30_percent_hotspots_reviewed() {
    treeRootHolder.setRoot(ROOT_PROJECT);
    fillComponentIssuesVisitorRule.setIssues(FILE_1_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_REVIEWED, RESOLUTION_FIXED),
      // Should not be taken into account
      newIssue());
    fillComponentIssuesVisitorRule.setIssues(FILE_2_REF,
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newHotspot(STATUS_TO_REVIEW, null),
      newIssue());

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(FILE_1_REF, D, 33.3);
    verifyMeasures(FILE_2_REF, E, 0.0);
    verifyMeasures(DIRECTORY_REF, E, 16.7);
    verifyMeasures(ROOT_DIR_REF, E, 16.7);
    verifyMeasures(PROJECT_REF, E, 16.7);
  }

  @Test
  public void compute_A_rating_and_100_percent_when_no_hotspot() {
    treeRootHolder.setRoot(ROOT_PROJECT);

    underTest.visit(ROOT_PROJECT);

    verifyMeasures(PROJECT_REF, A, 100.0);
  }

  private void verifyMeasures(int componentRef, Rating expectedReviewRating, double expectedHotspotsReviewed) {
    verifySecurityReviewRating(componentRef, expectedReviewRating);
    verifySecurityHotspotsReviewed(componentRef, expectedHotspotsReviewed);
  }

  private void verifySecurityReviewRating(int componentRef, Rating rating) {
    Measure measure = measureRepository.getAddedRawMeasure(componentRef, SECURITY_REVIEW_RATING_KEY).get();
    assertThat(measure.getIntValue()).isEqualTo(rating.getIndex());
    assertThat(measure.getData()).isEqualTo(rating.name());
  }

  private void verifySecurityHotspotsReviewed(int componentRef, double percent) {
    assertThat(measureRepository.getAddedRawMeasure(componentRef, SECURITY_HOTSPOTS_REVIEWED_KEY).get().getDoubleValue()).isEqualTo(percent);
  }

  private static DefaultIssue newHotspot(String status, @Nullable String resolution) {
    return new DefaultIssue()
      .setKey(Uuids.create())
      .setSeverity(MINOR)
      .setStatus(status)
      .setResolution(resolution)
      .setType(RuleType.SECURITY_HOTSPOT);
  }

  private static DefaultIssue newIssue() {
    return new DefaultIssue()
      .setKey(Uuids.create())
      .setSeverity(MAJOR)
      .setType(RuleType.BUG);
  }

}