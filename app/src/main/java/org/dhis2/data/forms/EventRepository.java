package org.dhis2.data.forms;

import android.content.ContentValues;
import android.database.Cursor;

import com.google.android.gms.maps.model.LatLng;
import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactoryImpl;
import org.dhis2.data.tuples.Pair;
import org.dhis2.data.tuples.Trio;
import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.category.CategoryComboModel;
import org.hisp.dhis.android.core.category.CategoryOptionComboModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRenderingModel;
import org.hisp.dhis.android.core.enrollment.EnrollmentModel;
import org.hisp.dhis.android.core.event.EventModel;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.program.ProgramModel;
import org.hisp.dhis.android.core.program.ProgramStageModel;
import org.hisp.dhis.android.core.program.ProgramStageSectionModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityDataValueModel;
import org.hisp.dhis.rules.RuleEngine;
import org.hisp.dhis.rules.RuleEngineContext;
import org.hisp.dhis.rules.RuleExpressionEvaluator;
import org.hisp.dhis.rules.models.TriggerEnvironment;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;

import static android.text.TextUtils.isEmpty;
import static org.dhis2.data.database.SqlConstants.ALL;
import static org.dhis2.data.database.SqlConstants.AS;
import static org.dhis2.data.database.SqlConstants.COMMA;
import static org.dhis2.data.database.SqlConstants.EQUAL;
import static org.dhis2.data.database.SqlConstants.FROM;
import static org.dhis2.data.database.SqlConstants.JOIN;
import static org.dhis2.data.database.SqlConstants.LEFT_OUTER_JOIN;
import static org.dhis2.data.database.SqlConstants.LIMIT_1;
import static org.dhis2.data.database.SqlConstants.ON;
import static org.dhis2.data.database.SqlConstants.ORDER_BY;
import static org.dhis2.data.database.SqlConstants.POINT;
import static org.dhis2.data.database.SqlConstants.QUESTION_MARK;
import static org.dhis2.data.database.SqlConstants.SELECT;
import static org.dhis2.data.database.SqlConstants.TABLE_POINT_FIELD_EQUALS;
import static org.dhis2.data.database.SqlConstants.WHERE;

public class EventRepository implements FormRepository {
    private static final List<String> TITLE_TABLES = Arrays.asList(
            ProgramModel.TABLE, ProgramStageModel.TABLE);

    private static final List<String> SECTION_TABLES = Arrays.asList(
            EventModel.TABLE, ProgramModel.TABLE, ProgramStageModel.TABLE, ProgramStageSectionModel.TABLE);

    private static final String SELECT_PROGRAM =
            SELECT + ProgramModel.TABLE + POINT + ALL +
                    FROM + ProgramModel.TABLE +
                    JOIN + EventModel.TABLE +
                    ON + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM +
                    EQUAL + ProgramModel.TABLE + POINT + ProgramModel.Columns.UID +
                    WHERE + EventModel.TABLE + POINT + EventModel.Columns.UID +
                    EQUAL + QUESTION_MARK + LIMIT_1;

    private static final String SELECT_PROGRAM_FROM_EVENT = String.format(
            SELECT + "%s.*" + FROM + "%s" + JOIN + "%s" + ON + TABLE_POINT_FIELD_EQUALS + "%s.%s" +
                    WHERE + "%s.%s" + EQUAL + QUESTION_MARK + LIMIT_1,
            ProgramModel.TABLE, ProgramModel.TABLE, EventModel.TABLE,
            EventModel.TABLE, EventModel.Columns.PROGRAM, ProgramModel.TABLE, ProgramModel.Columns.UID,
            EventModel.TABLE, EventModel.Columns.UID);

    private static final String SELECT_TITLE =
            SELECT + ProgramModel.TABLE + POINT + ProgramModel.Columns.DISPLAY_NAME + COMMA +
                    ProgramStageModel.TABLE + POINT + ProgramStageModel.Columns.DISPLAY_NAME +
                    FROM + EventModel.TABLE +
                    JOIN + ProgramModel.TABLE +
                    ON + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM +
                    EQUAL + ProgramModel.TABLE + POINT + ProgramModel.Columns.UID +
                    JOIN + ProgramStageModel.TABLE +
                    ON + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM_STAGE +
                    EQUAL + ProgramStageModel.TABLE + POINT + ProgramStageModel.Columns.UID +
                    WHERE + EventModel.TABLE + POINT + EventModel.Columns.UID +
                    EQUAL + QUESTION_MARK + LIMIT_1;

    private static final String SELECT_SECTIONS =
            SELECT + ProgramModel.TABLE + POINT + ProgramModel.Columns.UID + AS + "programUid" + COMMA +
                    ProgramStageModel.TABLE + POINT + ProgramStageModel.Columns.UID + AS + "programUid" + COMMA +
                    ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.UID + AS + "programStageSectionUid" + COMMA +
                    ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.DISPLAY_NAME + AS + "programStageDisplayName" + COMMA +
                    ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.MOBILE_RENDER_TYPE + AS + "renderType" + COMMA +
                    ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.SORT_ORDER + AS + "sectionOrder" +
                    FROM + EventModel.TABLE +
                    JOIN + ProgramModel.TABLE +
                    ON + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM +
                    EQUAL + ProgramModel.TABLE + POINT + ProgramModel.Columns.UID +
                    JOIN + ProgramStageModel.TABLE +
                    ON + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM_STAGE +
                    EQUAL + ProgramStageModel.TABLE + POINT + ProgramStageModel.Columns.UID +
                    LEFT_OUTER_JOIN + ProgramStageSectionModel.TABLE +
                    ON + ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.PROGRAM_STAGE +
                    EQUAL + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM_STAGE +
                    WHERE + EventModel.TABLE + POINT + EventModel.Columns.UID +
                    EQUAL + QUESTION_MARK +
                    ORDER_BY + ProgramStageSectionModel.TABLE + POINT + ProgramStageSectionModel.Columns.SORT_ORDER;

    private static final String SELECT_EVENT_STATUS =
            SELECT + EventModel.TABLE + POINT + EventModel.Columns.STATUS +
                    FROM + EventModel.TABLE +
                    WHERE + EventModel.TABLE + POINT + EventModel.Columns.UID +
                    EQUAL + QUESTION_MARK + LIMIT_1;

    private static final String QUERY = "SELECT\n" +
            "  Field.id,\n" +
            "  Field.label,\n" +
            "  Field.type,\n" +
            "  Field.mandatory,\n" +
            "  Field.optionSet,\n" +
            "  Value.value,\n" +
            "  Option.displayName,\n" +
            "  Field.section,\n" +
            "  Field.allowFutureDate,\n" +
            "  Event.status,\n" +
            "  Field.formLabel,\n" +
            "  Field.displayDescription,\n" +
            "  Field.formOrder,\n" +
            "  Field.sectionOrder\n" +
            "FROM Event\n" +
            "  LEFT OUTER JOIN (\n" +
            "      SELECT\n" +
            "        DataElement.displayName AS label,\n" +
            "        DataElement.displayFormName AS formLabel,\n" +
            "        DataElement.valueType AS type,\n" +
            "        DataElement.uid AS id,\n" +
            "        DataElement.optionSet AS optionSet,\n" +
            "        ProgramStageDataElement.sortOrder AS formOrder,\n" +
            "        ProgramStageDataElement.programStage AS stage,\n" +
            "        ProgramStageDataElement.compulsory AS mandatory,\n" +
            "        ProgramStageSectionDataElementLink.programStageSection AS section,\n" +
            "        ProgramStageDataElement.allowFutureDate AS allowFutureDate,\n" +
            "        DataElement.displayDescription AS displayDescription,\n" +
            "        ProgramStageSectionDataElementLink.sortOrder AS sectionOrder\n" +
            "      FROM ProgramStageDataElement\n" +
            "        INNER JOIN DataElement ON DataElement.uid = ProgramStageDataElement.dataElement\n" +
            "        LEFT JOIN ProgramStageSectionDataElementLink ON ProgramStageSectionDataElementLink.dataElement = ProgramStageDataElement.dataElement\n" +
            "    ) AS Field ON (Field.stage = Event.programStage)\n" +
            "  LEFT OUTER JOIN TrackedEntityDataValue AS Value ON (\n" +
            "    Value.event = Event.uid AND Value.dataElement = Field.id\n" +
            "  )\n" +
            "  LEFT OUTER JOIN Option ON (\n" +
            "    Field.optionSet = Option.optionSet AND Value.value = Option.code\n" +
            "  )\n" +
            " %s  " +
            "ORDER BY CASE" +
            " WHEN Field.sectionOrder IS NULL THEN Field.formOrder" +
            " WHEN Field.sectionOrder IS NOT NULL THEN Field.sectionOrder" +
            " END ASC;";

    @NonNull
    private final BriteDatabase briteDatabase;

    @NonNull
    private final Flowable<RuleEngine> cachedRuleEngineFlowable;

    @Nullable
    private final String eventUid;
    private String programUid;

    public EventRepository(@NonNull BriteDatabase briteDatabase,
                           @NonNull RuleExpressionEvaluator evaluator,
                           @NonNull RulesRepository rulesRepository,
                           @Nullable String eventUid) {
        this.briteDatabase = briteDatabase;
        this.eventUid = eventUid;

        // We don't want to rebuild RuleEngine on each request, since metadata of
        // the event is not changing throughout lifecycle of FormComponent.
        this.cachedRuleEngineFlowable = eventProgram()
                .switchMap(program -> Flowable.zip(
                        rulesRepository.rulesNew(program),
                        rulesRepository.ruleVariables(program),
                        rulesRepository.otherEvents(eventUid),
                        rulesRepository.enrollment(eventUid),
                        (rules, variables, events, enrollment) -> {

                            RuleEngine.Builder builder = RuleEngineContext.builder(evaluator)
                                    .rules(rules)
                                    .ruleVariables(variables)
                                    .calculatedValueMap(new HashMap<>())
                                    .supplementaryData(new HashMap<>())
                                    .build().toEngineBuilder();
                            builder.triggerEnvironment(TriggerEnvironment.ANDROIDCLIENT);
                            builder.events(events);
                            if (!isEmpty(enrollment.enrollment()))
                                builder.enrollment(enrollment);
                            return builder.build();
                        }))
                .cacheWithInitialCapacity(1);
    }

    @NonNull
    @Override
    public Flowable<RuleEngine> ruleEngine() {
        return cachedRuleEngineFlowable;
    }

    @NonNull
    @Override
    public Flowable<String> title() {
        return briteDatabase
                .createQuery(TITLE_TABLES, SELECT_TITLE, eventUid == null ? "" : eventUid)
                .mapToOne(cursor -> cursor.getString(0) + " - " + cursor.getString(1)).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<ProgramModel, String>> reportDate() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_PROGRAM, eventUid == null ? "" : eventUid)
                .mapToOne(ProgramModel::create)
                .map(programModel -> Pair.create(programModel, ""))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<ProgramModel, String>> incidentDate() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_PROGRAM, eventUid == null ? "" : eventUid)
                .mapToOne(ProgramModel::create)
                .map(programModel -> Pair.create(programModel, ""))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @Override
    public Flowable<ProgramModel> getAllowDatesInFuture() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_PROGRAM_FROM_EVENT, eventUid == null ? "" : eventUid)
                .mapToOne(ProgramModel::create)
                .toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Flowable<ReportStatus> reportStatus() {
        return briteDatabase
                .createQuery(EventModel.TABLE, SELECT_EVENT_STATUS, eventUid == null ? "" : eventUid)
                .mapToOne(cursor -> ReportStatus.fromEventStatus(EventStatus.valueOf(cursor.getString(0)))).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<List<FormSectionViewModel>> sections() {
        return briteDatabase
                .createQuery(SECTION_TABLES, SELECT_SECTIONS, eventUid == null ? "" : eventUid)
                .mapToList(cursor -> mapToFormSectionViewModels(eventUid == null ? "" : eventUid, cursor))
                .distinctUntilChanged().toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Consumer<String> storeReportDate() {
        return reportDate -> {
            Calendar cal = Calendar.getInstance();
            Date date = DateUtils.databaseDateFormat().parse(reportDate);
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            ContentValues event = new ContentValues();
            event.put(EventModel.Columns.EVENT_DATE, DateUtils.databaseDateFormat().format(cal.getTime()));
            event.put(EventModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            updateProgramTable(Calendar.getInstance().getTime(), programUid);

            briteDatabase.update(EventModel.TABLE, event, EventModel.Columns.UID + " = ?", eventUid == null ? "" : eventUid);
        };
    }

    @NonNull
    @Override
    public Consumer<String> storeIncidentDate() {
        return data -> {
            //incident date is only for tracker events
        };
    }

    @NonNull
    @Override
    public Consumer<LatLng> storeCoordinates() {
        return data -> {
            //coordinates are only for tracker events
        };
    }

    @NonNull
    @Override
    public Consumer<ReportStatus> storeReportStatus() {
        return reportStatus -> {
            ContentValues event = new ContentValues();
            event.put(EventModel.Columns.STATUS, ReportStatus.toEventStatus(reportStatus).name());
            event.put(EventModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            updateProgramTable(Calendar.getInstance().getTime(), programUid);

            briteDatabase.update(EventModel.TABLE, event, EventModel.Columns.UID + " = ?", eventUid == null ? "" : eventUid);
        };
    }

    @NonNull
    @Override
    public Observable<Trio<String, String, String>> useFirstStageDuringRegistration() {
        return Observable.just(null);
    }

    @NonNull
    @Override
    public Observable<String> autoGenerateEvents(String enrollmentUid) {
        return null;
    }

    @NonNull
    @Override
    public Observable<List<FieldViewModel>> fieldValues() {
        String where = String.format(Locale.US, "WHERE Event.uid = '%s'", eventUid == null ? "" : eventUid);
        return briteDatabase.createQuery(TrackedEntityDataValueModel.TABLE, String.format(Locale.US, QUERY, where))
                .mapToList(this::transform);
    }

    @Override
    public void deleteTrackedEntityAttributeValues(@NonNull String trackedEntityInstanceId) {
        // not necessary
    }

    @Override
    public void deleteEnrollment(@NonNull String trackedEntityInstanceId) {
        // not necessary
    }

    @Override
    public void deleteEvent() {
        String DELETE_WHERE_RELATIONSHIP = String.format(
                "%s.%s = ",
                EventModel.TABLE, EventModel.Columns.UID);
        String id = eventUid == null ? "" : eventUid;
        briteDatabase.delete(EventModel.TABLE, DELETE_WHERE_RELATIONSHIP + "'" + id + "'");
    }

    @Override
    public void deleteTrackedEntityInstance(@NonNull String trackedEntityInstanceId) {
        // not necessary
    }

    @NonNull
    @Override
    public Observable<String> getTrackedEntityInstanceUid() {
        String SELECT_TE = "SELECT " + EventModel.TABLE + "." + EventModel.Columns.TRACKED_ENTITY_INSTANCE +
                " FROM " + EventModel.TABLE +
                " WHERE " + EventModel.Columns.UID + " = ? LIMIT 1";
        return briteDatabase.createQuery(EnrollmentModel.TABLE, SELECT_TE, eventUid == null ? "" : eventUid).mapToOne(cursor -> cursor.getString(0));
    }

    @Override
    public Observable<Trio<Boolean, CategoryComboModel, List<CategoryOptionComboModel>>> getProgramCategoryCombo() {
        return briteDatabase.createQuery(EventModel.TABLE, "SELECT * FROM Event WHERE Event.uid = ?", eventUid)
                .mapToOne(EventModel::create)
                .flatMap(eventModel -> briteDatabase.createQuery(CategoryComboModel.TABLE, "SELECT CategoryCombo.* FROM CategoryCombo " +
                        "JOIN Program ON Program.categoryCombo = CategoryCombo.uid WHERE Program.uid = ?", eventModel.program())
                        .mapToOne(CategoryComboModel::create)
                        .flatMap(categoryComboModel ->
                                briteDatabase.createQuery(CategoryOptionComboModel.TABLE, "SELECT * FROM CategoryOptionCombo " +
                                        "WHERE categoryCombo = ?", categoryComboModel.uid())
                                        .mapToList(CategoryOptionComboModel::create)
                                        .map(categoryOptionComboModels -> {
                                            boolean eventHastOptionSelected = false;
                                            for (CategoryOptionComboModel options : categoryOptionComboModels) {
                                                if (eventModel.attributeOptionCombo() != null && eventModel.attributeOptionCombo().equals(options.uid()))
                                                    eventHastOptionSelected = true;
                                            }
                                            return Trio.create(eventHastOptionSelected, categoryComboModel, categoryOptionComboModels);
                                        })
                        )
                );

    }

    @Override
    public void saveCategoryOption(CategoryOptionComboModel selectedOption) {
        ContentValues event = new ContentValues();
        event.put(EventModel.Columns.ATTRIBUTE_OPTION_COMBO, selectedOption.uid());
        event.put(EventModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
        // TODO: and if so, keep the TO_POST state

        briteDatabase.update(EventModel.TABLE, event, EventModel.Columns.UID + " = ?", eventUid == null ? "" : eventUid);
    }

    @Override
    public Observable<Boolean> captureCoodinates() {
        return briteDatabase.createQuery("ProgramStage", "SELECT ProgramStage.captureCoordinates FROM ProgramStage " +
                "JOIN Event ON Event.programStage = ProgramStage.uid WHERE Event.uid = ?", eventUid)
                .mapToOne(cursor -> cursor.getInt(0) == 1);
    }

    @NonNull
    private FieldViewModel transform(@NonNull Cursor cursor) {
        String uid = cursor.getString(0);
        String label = cursor.getString(1);
        ValueType valueType = ValueType.valueOf(cursor.getString(2));
        boolean mandatory = cursor.getInt(3) == 1;
        String optionSetUid = cursor.getString(4);
        String dataValue = cursor.getString(5);
        String optionCodeName = cursor.getString(6);
        String section = cursor.getString(7);
        Boolean allowFutureDates = cursor.getInt(8) == 1;
        EventStatus status = EventStatus.valueOf(cursor.getString(9));
        String formLabel = cursor.getString(10);
        String description = cursor.getString(11);
        if (!isEmpty(optionCodeName)) {
            dataValue = optionCodeName;
        }

        ValueTypeDeviceRenderingModel fieldRendering = null;
        Cursor rendering = briteDatabase.query("SELECT * FROM ValueTypeDeviceRendering WHERE uid = ?", uid);
        if (rendering != null && rendering.moveToFirst()) {
            fieldRendering = ValueTypeDeviceRenderingModel.create(cursor);
            rendering.close();
        }

        FieldViewModelFactoryImpl fieldFactory = new FieldViewModelFactoryImpl(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");

        return fieldFactory.create(uid, isEmpty(formLabel) ? label : formLabel, valueType,
                mandatory, optionSetUid, dataValue, section, allowFutureDates,
                status == EventStatus.ACTIVE, null, description, fieldRendering);
    }

    @NonNull
    private Flowable<String> eventProgram() {
        return briteDatabase.createQuery(EventModel.TABLE, SELECT_PROGRAM, eventUid == null ? "" : eventUid)
                .mapToOne(ProgramModel::create)
                .map(programModel -> {
                    programUid = programModel.uid();
                    return programUid;
                }).toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    private FormSectionViewModel mapToFormSectionViewModels(@NonNull String eventUid, @NonNull Cursor cursor) {
        if (cursor.getString(2) == null) {
            // This programstage has no sections
            return FormSectionViewModel.createForProgramStage(
                    eventUid, cursor.getString(1));
        } else {
            // This programstage has sections
            return FormSectionViewModel.createForSection(
                    eventUid, cursor.getString(2), cursor.getString(3), cursor.getString(4));
        }
    }

    private void updateProgramTable(Date lastUpdated, String programUid) {
        /*ContentValues program = new ContentValues();TODO: Crash if active
        program.put(EnrollmentModel.Columns.LAST_UPDATED, BaseIdentifiableObject.DATE_FORMAT.format(lastUpdated));
        briteDatabase.update(ProgramModel.TABLE, program, ProgramModel.Columns.UID + " = ?", programUid);*/
    }
}