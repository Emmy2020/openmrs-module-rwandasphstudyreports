/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 *  obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.module.rwandasphstudyreports.api;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.CompositionCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.EncounterCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.InProgramCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.InStateCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.SqlCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.service.CohortDefinitionService;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.DurationUnit;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.service.DataSetDefinitionService;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.evaluation.parameter.ParameterizableUtil;
import org.openmrs.module.rowperpatientreports.dataset.definition.RowPerPatientDataSetDefinition;
import org.openmrs.module.rowperpatientreports.patientdata.definition.DateDiff;
import org.openmrs.module.rowperpatientreports.patientdata.definition.DateDiff.DateDiffType;
import org.openmrs.module.rowperpatientreports.patientdata.definition.DateOfBirthShowingEstimation;
import org.openmrs.module.rowperpatientreports.patientdata.definition.MostRecentObservation;
import org.openmrs.module.rowperpatientreports.patientdata.definition.MultiplePatientDataDefinitions;
import org.openmrs.module.rowperpatientreports.patientdata.definition.PatientAddress;
import org.openmrs.module.rowperpatientreports.patientdata.definition.PatientProperty;
import org.openmrs.module.rowperpatientreports.patientdata.definition.PatientRelationship;
import org.openmrs.module.rowperpatientreports.patientdata.definition.StateOfPatient;
import org.openmrs.module.rwandareports.filter.GroupStateFilter;
import org.openmrs.module.rwandareports.util.Cohorts;
import org.openmrs.module.rwandareports.util.GlobalPropertiesManagement;
import org.openmrs.module.rwandasphstudyreports.RowPerPatientColumns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Tests elements of the SetupAdultLateVisitAndCD4Report class
 * 
 * Concentrating on specifically these indicators; [HIV-Adult ART
 * Report-Monthly: AdultARTLateVisit, AdultHIVLateCD4Count, HIVLostToFollowup,
 * HIVLowBMI, ViralLoadGreaterThan20InLast3Months] [HIV-Adult Pre ART
 * Report-Monthly: AdultPreARTLateVisit, AdultHIVLateCD4Count,
 * HIVLostToFollowup, PreARTBelow350CD4, HIVLowBMI]
 */
public class SetupAdultLateVisitAndCD4ReportTest extends StandaloneContextSensitiveTest {

	@Autowired
	@Qualifier(value = "reportingCohortDefinitionService")
	CohortDefinitionService cohortDefinitionService;

	@Autowired
	@Qualifier(value = "locationService")
	LocationService locationService;

	EvaluationContext context;
	GlobalPropertiesManagement gp;
	Concept cd4;
	Program hivProgram;
	ProgramWorkflowState onART;
	ProgramWorkflowState following;
	ProgramWorkflow treatmentGroup;
	ProgramWorkflow treatmentStatus;
	Concept viralLoad, height, weight;
	Integer labEncounterTypeId, cd4ConceptId, viralLoadConceptId;
	InProgramCohortDefinition adultHivProgramCohort;
	InStateCohortDefinition onARTStatusCohort;
	List<EncounterType> clinicalEncoutersExcLab;
	EncounterCohortDefinition patientsWithClinicalEncountersWithoutLabTest;
	CompositionCohortDefinition patientsWithClinicalEncounters;
	SqlCohortDefinition patientsWithViralLoadAndCD4Tested ;
	CompositionCohortDefinition patientsWithoutClinicalEncounters;
	MultiplePatientDataDefinitions imbType;
	MostRecentObservation mostRecentViralLoads;
	PatientProperty givenName, familyName, gender;
	DateOfBirthShowingEstimation birthdate;
	StateOfPatient txGroup;
	MostRecentObservation returnVisitDate, cd4Count;
	DateDiff lateCD4InMonths;
	PatientRelationship accompagnateur;
	PatientAddress address;
	MultiplePatientDataDefinitions tracNetId;
	Map<String, Object> mappings;
	
	@Before
	public void runBeforeTesting() {
		context = new EvaluationContext();

		context.addParameterValue("beforeDate", new Date());
		context.addParameterValue("onDate", new Date());
		context.addParameterValue("location", locationService
				.getLocation(54));/*
									 * 'Gahini HD' contains more data
									 */
		context.addParameterValue("onOrAfter", DateUtil.adjustDate(new Date(), -12, DurationUnit.MONTHS));

		gp = new GlobalPropertiesManagement();
		cd4 = gp.getConcept(GlobalPropertiesManagement.CD4_TEST);
		hivProgram = gp.getProgram(GlobalPropertiesManagement.ADULT_HIV_PROGRAM);
		onART = gp.getProgramWorkflowState(GlobalPropertiesManagement.ON_ANTIRETROVIRALS_STATE,
				GlobalPropertiesManagement.TREATMENT_STATUS_WORKFLOW, GlobalPropertiesManagement.ADULT_HIV_PROGRAM);
		following = gp.getProgramWorkflowState(GlobalPropertiesManagement.FOLLOWING_STATE,
				GlobalPropertiesManagement.TREATMENT_STATUS_WORKFLOW, GlobalPropertiesManagement.ADULT_HIV_PROGRAM);
		treatmentGroup = gp.getProgramWorkflow(GlobalPropertiesManagement.TREATMENT_GROUP_WORKFLOW,
				GlobalPropertiesManagement.ADULT_HIV_PROGRAM);
		treatmentStatus = gp.getProgramWorkflow(GlobalPropertiesManagement.TREATMENT_STATUS_WORKFLOW,
				GlobalPropertiesManagement.ADULT_HIV_PROGRAM);
		adultHivProgramCohort = Cohorts
				.createInProgramParameterizableByDate("adultHivProgramCohort", hivProgram);

		viralLoad = gp.getConcept(GlobalPropertiesManagement.VIRAL_LOAD_TEST);
		labEncounterTypeId = gp.getEncounterType(GlobalPropertiesManagement.LAB_ENCOUNTER_TYPE).getEncounterTypeId();
		cd4ConceptId = gp.getConcept(GlobalPropertiesManagement.CD4_TEST).getConceptId();
		viralLoadConceptId = gp.getConcept(GlobalPropertiesManagement.VIRAL_LOAD_TEST).getConceptId();
		onARTStatusCohort = Cohorts
				.createInProgramStateParameterizableByDate("onARTStatusCohort", onART);
		clinicalEncoutersExcLab = gp
				.getEncounterTypeList(GlobalPropertiesManagement.CLINICAL_ENCOUNTER_TYPES_EXC_LAB_TEST);

		patientsWithViralLoadAndCD4Tested = new SqlCohortDefinition(
				"SELECT distinct e.patient_id FROM encounter e , obs o where o.encounter_id=e.encounter_id and e.encounter_type="
						+ labEncounterTypeId + " and o.concept_id in (" + viralLoadConceptId + "," + cd4ConceptId
						+ ") and e.encounter_datetime>= :onOrAfter and e.voided=0 and o.voided=0 and value_numeric is not null;");
		patientsWithViralLoadAndCD4Tested.addParameter(new Parameter("onOrAfter", "onOrAfter", Date.class));

		patientsWithClinicalEncountersWithoutLabTest = Cohorts
				.createEncounterParameterizedByDate("patientsWithClinicalEncounters", "onOrAfter",
						clinicalEncoutersExcLab);

		patientsWithClinicalEncounters = new CompositionCohortDefinition();
		patientsWithClinicalEncounters.addParameter(new Parameter("onOrAfter", "onOrAfter", Date.class));
		patientsWithClinicalEncounters.getSearches().put("1",
				new Mapped<CohortDefinition>(patientsWithViralLoadAndCD4Tested,
						ParameterizableUtil.createParameterMappings("onOrAfter=${onOrAfter}")));
		patientsWithClinicalEncounters.getSearches().put("2",
				new Mapped<CohortDefinition>(patientsWithClinicalEncountersWithoutLabTest,
						ParameterizableUtil.createParameterMappings("onOrAfter=${onOrAfter}")));
		patientsWithClinicalEncounters.setCompositionString("1 OR 2");
		
		patientsWithoutClinicalEncounters = new CompositionCohortDefinition();
		patientsWithoutClinicalEncounters.setName("patientsWithoutClinicalEncounters");
		patientsWithoutClinicalEncounters.addParameter(new Parameter("onOrAfter", "onOrAfter", Date.class));
		patientsWithoutClinicalEncounters.setCompositionString("NOT patientsWithClinicalEncountersWithoutLabTest");

		imbType = RowPerPatientColumns.getIMBId("IMB ID");
		mostRecentViralLoads = RowPerPatientColumns.getMostRecentViralLoad("Most recent viralLoad", null);
		givenName = RowPerPatientColumns.getFirstNameColumn("First Name");
		familyName = RowPerPatientColumns.getFamilyNameColumn("Last Name");
		gender = RowPerPatientColumns.getGender("Sex");
		
		birthdate = RowPerPatientColumns.getDateOfBirth("Date of Birth", null, null);
		
		txGroup = RowPerPatientColumns.getStateOfPatient("Group", hivProgram, treatmentGroup,
				new GroupStateFilter());
		
		returnVisitDate = RowPerPatientColumns
				.getMostRecentReturnVisitDate("Date of missed appointment", null);
		cd4Count = RowPerPatientColumns.getMostRecentCD4("Most recent CD4", null);
		
		lateCD4InMonths = RowPerPatientColumns.getDifferenceSinceLastObservation("Late CD4 in months", cd4,
				DateDiffType.MONTHS);
		
		accompagnateur = RowPerPatientColumns.getAccompRelationship("Accompagnateur");
	
		address = RowPerPatientColumns.getPatientAddress("Address", true, true, true, true);
		
		tracNetId = RowPerPatientColumns.getTracnetId("TRACNET_ID");

		mappings = new HashMap<String, Object>();
		mappings.put("location", "${location}");
		mappings.put("endDate", "${endDate}");
		
		height = gp.getConcept(GlobalPropertiesManagement.HEIGHT_CONCEPT);
		weight = gp.getConcept(GlobalPropertiesManagement.WEIGHT_CONCEPT);
	}

	@Test
	public void test_PatientWithViralLoadAndCD4TestedCohort() throws Exception {
		System.out.println();

		Cohort r1 = cohortDefinitionService.evaluate(patientsWithViralLoadAndCD4Tested, context);
		System.out.println("patientsWithViralLoadAndCD4Tested: " + r1.size());

		
		Cohort r2 = cohortDefinitionService.evaluate(patientsWithClinicalEncountersWithoutLabTest, context);
		System.out.println("patientsWithClinicalEncountersWithoutLabTest: " + r2.size());

		Cohort r3 = cohortDefinitionService.evaluate(patientsWithClinicalEncounters, context);
		System.out.println("union of these: " + r3.size());
		System.out.println();
	}

	@Test
	public void test_ViralLoadGreaterThan1000InLast12Months() throws EvaluationException {
		Integer viralLoadConceptId = gp.getConcept(GlobalPropertiesManagement.VIRAL_LOAD_TEST).getConceptId();

		System.out.println();
		SqlCohortDefinition viralLoadGreaterThan1000InLast12Months = new SqlCohortDefinition(
				"select vload.person_id from (select * from obs where concept_id=" + viralLoadConceptId
						+ " and value_numeric>1000 and obs_datetime> :beforeDate and obs_datetime<= :onDate order by obs_datetime desc) as vload group by vload.person_id");
		viralLoadGreaterThan1000InLast12Months.setName("viralLoadGreaterThan1000InLast12Months");
		viralLoadGreaterThan1000InLast12Months.addParameter(new Parameter("beforeDate", "beforeDate", Date.class));
		viralLoadGreaterThan1000InLast12Months.addParameter(new Parameter("onDate", "onDate", Date.class));
		viralLoadGreaterThan1000InLast12Months.addParameter(new Parameter("location", "location", Location.class));

		Cohort r = cohortDefinitionService.evaluate(viralLoadGreaterThan1000InLast12Months, context);
		System.out.println("viralLoadGreaterThan1000InLast12Months: " + r.size());
		System.out.println();
	}

	@Test
	public void test_MainReportsMetadata() throws EvaluationException {
		SqlCohortDefinition deciningInCD4MoreThan50 = Cohorts.createPatientsWithDecline("deciningInCD4MoreThan50", cd4,
				50);

		System.out.println("\nMETADATA COUNTS:");
		InStateCohortDefinition followingStatusCohort = Cohorts
				.createInProgramStateParameterizableByDate("followingStatusCohort", following);
		System.out.println(
				"adultHivProgramCohort: " + cohortDefinitionService.evaluate(adultHivProgramCohort, context).size());
		System.out.println("onARTStatusCohort: " + cohortDefinitionService.evaluate(onARTStatusCohort, context).size());
		System.out.println(
				"followingStatusCohort: " + cohortDefinitionService.evaluate(followingStatusCohort, context).size());
		System.out.println("deciningInCD4MoreThan50: "
				+ cohortDefinitionService.evaluate(deciningInCD4MoreThan50, context).size());
		System.out.println();
	}

	@Test
	public void test_patientWithLowBMI() throws EvaluationException {
		SqlCohortDefinition patientWithLowBMI = new SqlCohortDefinition();
		patientWithLowBMI.setName("patientWithLowBMI");
		System.out.println();
		patientWithLowBMI.setQuery(
				"select w.person_id from (select * from (select o.person_id,o.value_numeric from obs o,concept c where o.voided=0 and o.value_numeric is not null and o.concept_id= c.concept_id and c.concept_id='"
						+ height.getId()
						+ "' order by o.obs_datetime desc) as lastheight group by lastheight.person_id) h,(select * from (select o.person_id,o.value_numeric from obs o,concept c where o.voided=0 and o.value_numeric is not null and o.concept_id= c.concept_id and c.uuid='"
						+ weight.getUuid()
						+ "' order by o.obs_datetime desc) as lastweight group by lastweight.person_id) w,(select p.patient_id from patient p, person_attribute pa, person_attribute_type pat where p.patient_id = pa.person_id and pat.name ='Health Center' and pat.person_attribute_type_id = pa.person_attribute_type_id and pa.voided = 0 and pa.value = :location) loc where loc.patient_id=w.person_id and w.person_id=h.person_id and ROUND(((w.value_numeric*10000)/(h.value_numeric*h.value_numeric)),2)<16.0");
		patientWithLowBMI.addParameter(new Parameter("location", "location", Location.class));

		Cohort r = cohortDefinitionService.evaluate(patientWithLowBMI, context);
		System.out.println("patientWithLowBMI: " + r.size());
		System.out.println();
	}

	@Test
	public void test_adultARTLateVisit_dataSetDefinition() throws EvaluationException {
		RowPerPatientDataSetDefinition adultARTLateVisit = new RowPerPatientDataSetDefinition();
		
		adultARTLateVisit.setName("Adult ART dataSetDefinition");
		adultARTLateVisit.addParameter(new Parameter("location", "Location", Location.class));
		adultARTLateVisit.addParameter(new Parameter("endDate", "End Date", Date.class));
		
		/*adultARTLateVisit.addFilter(adultHivProgramCohort, ParameterizableUtil.createParameterMappings("onDate=${endDate}"));
		adultARTLateVisit.addFilter(onARTStatusCohort, ParameterizableUtil.createParameterMappings("onDate=${endDate}"));
		adultARTLateVisit.addFilter(patientsWithClinicalEncounters, ParameterizableUtil.createParameterMappings("onDate=${endDate}"));
		adultARTLateVisit.addFilter(patientsWithoutClinicalEncounters, ParameterizableUtil.createParameterMappings("onDate=${endDate}"));
		*/adultARTLateVisit.addColumn(imbType, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(givenName, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(familyName, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(gender, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(birthdate, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(txGroup, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(returnVisitDate, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(cd4Count, new HashMap<String, Object>()); 
		//adultARTLateVisit.addColumn(lateCD4InMonths, ParameterizableUtil.createParameterMappings("endDate=${endDate}"));
		adultARTLateVisit.addColumn(accompagnateur, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(address, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(tracNetId, new HashMap<String, Object>()); 
		adultARTLateVisit.addColumn(mostRecentViralLoads, new HashMap<String, Object>()); 
	
		EvaluationContext ec = EvaluationContext.cloneForChild(context, new Mapped<RowPerPatientDataSetDefinition>(adultARTLateVisit,
		        mappings));
		
		SimpleDataSet adultARTLAteVisitDataSet = (SimpleDataSet) Context.getService(DataSetDefinitionService.class).evaluate(adultARTLateVisit, ec);
		System.out.println("adultARTLateVisit: " + adultARTLAteVisitDataSet.getRowMap().size());
		System.out.println();
	}
}