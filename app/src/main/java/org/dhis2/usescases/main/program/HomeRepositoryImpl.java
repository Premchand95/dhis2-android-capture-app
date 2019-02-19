package org.dhis2.usescases.main.program;

import android.database.Cursor;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.tuples.Pair;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.Period;
import org.hisp.dhis.android.core.common.ObjectStyleModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.dataset.DataSetDataElementLinkModel;
import org.hisp.dhis.android.core.enrollment.EnrollmentModel;
import org.hisp.dhis.android.core.event.EventModel;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitModel;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitProgramLinkModel;
import org.hisp.dhis.android.core.program.ProgramModel;
import org.hisp.dhis.android.core.program.ProgramType;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityTypeModel;
import org.hisp.dhis.android.core.user.UserOrganisationUnitLinkModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

import static org.dhis2.data.database.SqlConstants.ALL;
import static org.dhis2.data.database.SqlConstants.AND;
import static org.dhis2.data.database.SqlConstants.ASC;
import static org.dhis2.data.database.SqlConstants.COMMA;
import static org.dhis2.data.database.SqlConstants.EQUAL;
import static org.dhis2.data.database.SqlConstants.FROM;
import static org.dhis2.data.database.SqlConstants.GROUP_BY;
import static org.dhis2.data.database.SqlConstants.JOIN;
import static org.dhis2.data.database.SqlConstants.LIMIT_1;
import static org.dhis2.data.database.SqlConstants.NOT_EQUAL;
import static org.dhis2.data.database.SqlConstants.ON;
import static org.dhis2.data.database.SqlConstants.ORDER_BY;
import static org.dhis2.data.database.SqlConstants.POINT;
import static org.dhis2.data.database.SqlConstants.QUESTION_MARK;
import static org.dhis2.data.database.SqlConstants.QUOTE;
import static org.dhis2.data.database.SqlConstants.SELECT;
import static org.dhis2.data.database.SqlConstants.VARIABLE;
import static org.dhis2.data.database.SqlConstants.WHERE;

class HomeRepositoryImpl implements HomeRepository {

    private static final String SELECT_EVENTS = SELECT + EventModel.TABLE + POINT + ALL + FROM + EventModel.TABLE +
            WHERE + VARIABLE + " " + EventModel.TABLE + POINT + EventModel.Columns.PROGRAM + EQUAL + QUESTION_MARK +
            AND + EventModel.TABLE + POINT + EventModel.Columns.STATE + NOT_EQUAL + QUOTE + State.TO_DELETE + QUOTE;

    private static final String SELECT_TEIS = SELECT + EventModel.TABLE + POINT + ALL + FROM + EventModel.TABLE +
            JOIN + EnrollmentModel.TABLE +
            ON + EnrollmentModel.TABLE + POINT + EnrollmentModel.Columns.UID +
            EQUAL + EventModel.TABLE + POINT + EventModel.Columns.ENROLLMENT +
            WHERE + VARIABLE + " " +
            EventModel.TABLE + POINT + EventModel.Columns.PROGRAM + EQUAL + QUESTION_MARK +
            AND + EventModel.TABLE + POINT + EventModel.Columns.STATE + NOT_EQUAL + QUOTE + State.TO_DELETE + QUOTE +
            GROUP_BY + EnrollmentModel.TABLE + POINT + EnrollmentModel.Columns.TRACKED_ENTITY_INSTANCE;

    private static final String TRACKED_ENTITY_TYPE_NAME = SELECT +
            TrackedEntityTypeModel.TABLE + POINT + TrackedEntityTypeModel.Columns.DISPLAY_NAME +
            FROM + TrackedEntityTypeModel.TABLE +
            WHERE + TrackedEntityTypeModel.TABLE + POINT + TrackedEntityTypeModel.Columns.UID +
            EQUAL + QUESTION_MARK + LIMIT_1;

    private static final String PROGRAM_MODELS = SELECT +
            "Program.uid, " +
            "Program.displayName, " +
            "ObjectStyle.color, " +
            "ObjectStyle.icon," +
            "Program.programType," +
            "Program.trackedEntityType," +
            "Program.description " +
            "FROM Program LEFT JOIN ObjectStyle ON ObjectStyle.uid = Program.uid " +
            "JOIN OrganisationUnitProgramLink ON OrganisationUnitProgramLink.program = Program.uid %s GROUP BY Program.uid " /*+
            "UNION " +
            "SELECT DataSet.uid, " +
            "DataSet.displayName, " +
            "null, " +
            "null, " +
            "'', " +
            "'', " +
            "DataSet.description " +
            "FROM DataSet " +
            "JOIN DataSetOrganisationUnitLink ON DataSetOrganisationUnitLink.dataSet = DataSet.uid GROUP BY DataSet.uid"*/;

    private static final String AGGREGATE_FROM_DATASET = SELECT + ALL + FROM + DataSetDataElementLinkModel.TABLE +
            WHERE + DataSetDataElementLinkModel.Columns.DATA_SET + EQUAL + QUESTION_MARK;

    private static final String[] TABLE_NAMES = new String[]{ProgramModel.TABLE, ObjectStyleModel.TABLE, OrganisationUnitProgramLinkModel.TABLE};
    private static final Set<String> TABLE_SET = new HashSet<>(Arrays.asList(TABLE_NAMES));

    private static final String SELECT_ORG_UNITS =
            SELECT + ALL + FROM + OrganisationUnitModel.TABLE + COMMA + UserOrganisationUnitLinkModel.TABLE +
                    WHERE + OrganisationUnitModel.TABLE + POINT + OrganisationUnitModel.Columns.UID + EQUAL +
                    UserOrganisationUnitLinkModel.TABLE + POINT + UserOrganisationUnitLinkModel.Columns.ORGANISATION_UNIT +
                    AND + UserOrganisationUnitLinkModel.TABLE + POINT + UserOrganisationUnitLinkModel.Columns.ORGANISATION_UNIT_SCOPE + EQUAL +
                    QUOTE + OrganisationUnitModel.Scope.SCOPE_DATA_CAPTURE + QUOTE +
                    ORDER_BY + OrganisationUnitModel.TABLE + POINT + OrganisationUnitModel.Columns.DISPLAY_NAME + ASC;

    private final BriteDatabase briteDatabase;

    HomeRepositoryImpl(BriteDatabase briteDatabase) {
        this.briteDatabase = briteDatabase;
    }

    @NonNull
    @Override
    public Observable<Pair<Integer, String>> numberOfRecords(ProgramModel program) {
        String queryFinal = "";

        String id = program == null || program.uid() == null ? "" : program.uid();

        return briteDatabase.createQuery(EventModel.TABLE, queryFinal, id).mapToList(EventModel::create)
                .flatMap(data -> {
                    if (program.programType() == ProgramType.WITH_REGISTRATION)
                        return briteDatabase.createQuery(TrackedEntityTypeModel.TABLE, TRACKED_ENTITY_TYPE_NAME, program.trackedEntityType())
                                .mapToOne(cursor -> Pair.create(data.size(), cursor.getString(0)));
                    else
                        return Observable.just(Pair.create(data.size(), "events"));
                });
    }

    private StringBuilder generateDateQuery(List<Date> dates, Period period) {
        StringBuilder dateQuery = new StringBuilder("");
        if (dates != null && !dates.isEmpty()) {
            String queryFormat = "(%s BETWEEN '%s' AND '%s') ";
            for (int i = 0; i < dates.size(); i++) {
                Date[] datesToQuery = DateUtils.getInstance().getDateFromDateAndPeriod(dates.get(i), period);
                dateQuery.append(String.format(queryFormat, "Event.eventDate", DateUtils.databaseDateFormat().format(datesToQuery[0]), DateUtils.databaseDateFormat().format(datesToQuery[1])));
                if (i < dates.size() - 1)
                    dateQuery.append("OR ");
            }
        }
        return dateQuery;
    }

    private String queryDates(List<Date> dates, Period period, String programType) {
        String queryFinal;
        if (!programType.isEmpty()) {

            StringBuilder dateQuery = generateDateQuery(dates, period);
            String filter = "";
            if (!dateQuery.toString().isEmpty())
                filter = dateQuery.toString() + AND;

            if (programType.equals(ProgramType.WITH_REGISTRATION.name())) {
                queryFinal = String.format(SELECT_TEIS, filter);
            } else {
                queryFinal = String.format(SELECT_EVENTS, filter);
            }
        } else {
            queryFinal = AGGREGATE_FROM_DATASET;
        }

        return queryFinal;
    }

    private int queryCount(String queryFinal, String uid) {
        Cursor countCursor = briteDatabase.query(queryFinal, uid);
        int count = 0;
        if (countCursor != null && countCursor.moveToFirst()) {
            count = countCursor.getCount();
            countCursor.close();
        }
        return count;
    }

    private String queryTrackerName(String programType, String teiType) {
        String typeName = "";
        if (programType.equals(ProgramType.WITH_REGISTRATION.name())) {
            Cursor typeCursor = briteDatabase.query(TRACKED_ENTITY_TYPE_NAME, teiType);
            if (typeCursor != null && typeCursor.moveToFirst()) {
                typeName = typeCursor.getString(0);
                typeCursor.close();
            }
        } else if (programType.equals(ProgramType.WITHOUT_REGISTRATION.name())) {
            typeName = "Events";
        } else {
            typeName = "DataSets";
        }
        return typeName;
    }

    @NonNull
    @Override
    public Flowable<List<ProgramViewModel>> programModels(List<Date> dates, Period period, String orgUnitsId, int orgUnitsSize) {
        //QUERYING Program - orgUnit filter
        String orgQuery = "";
        if (orgUnitsId != null)
            orgQuery = String.format("WHERE OrganisationUnitProgramLink.organisationUnit IN (%s)", orgUnitsId);

        String programQuery = PROGRAM_MODELS.replace("%s", orgQuery);

        return briteDatabase.createQuery(TABLE_SET, programQuery)
                .mapToList(cursor -> {
                    String uid = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String color = cursor.getString(2);
                    String icon = cursor.getString(3);
                    String programType = cursor.getString(4);
                    String teiType = cursor.getString(5);
                    String description = cursor.getString(6);

                    //QUERYING Program EVENTS - dates filter
                    String queryFinal = queryDates(dates, period, programType);
                    int count = queryCount(queryFinal, uid);

                    //QUERYING Tracker name
                    String typeName = queryTrackerName(programType, teiType);

                    return ProgramViewModel.create(uid, displayName, color, icon, count, teiType, typeName, programType, description, true, true);
                }).map(list -> checkCount(list, period)).toFlowable(BackpressureStrategy.LATEST);
    }

    private List<ProgramViewModel> checkCount(List<ProgramViewModel> list, Period period) {
        if (period == null)
            return list;
        else {
            List<ProgramViewModel> models = new ArrayList<>();
            for (ProgramViewModel programViewModel : list)
                if (programViewModel.count() > 0)
                    models.add(programViewModel);
            return models;
        }
    }

    @NonNull
    @Override
    public Observable<List<OrganisationUnitModel>> orgUnits() {
        return briteDatabase.createQuery(OrganisationUnitModel.TABLE, SELECT_ORG_UNITS)
                .mapToList(OrganisationUnitModel::create);
    }
}
