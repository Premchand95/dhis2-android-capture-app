package org.dhis2.usescases.datasets.datasetDetail;

import android.database.Cursor;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.category.CategoryOptionComboModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.dataset.DataSetDataElementLinkModel;
import org.hisp.dhis.android.core.datavalue.DataValueModel;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitModel;
import org.hisp.dhis.android.core.period.PeriodModel;
import org.hisp.dhis.android.core.period.PeriodType;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

import static org.dhis2.data.database.SqlConstants.ALL;
import static org.dhis2.data.database.SqlConstants.AND;
import static org.dhis2.data.database.SqlConstants.COMMA;
import static org.dhis2.data.database.SqlConstants.EQUAL;
import static org.dhis2.data.database.SqlConstants.FROM;
import static org.dhis2.data.database.SqlConstants.GROUP_BY;
import static org.dhis2.data.database.SqlConstants.JOIN;
import static org.dhis2.data.database.SqlConstants.NOT_EQUAL;
import static org.dhis2.data.database.SqlConstants.ON;
import static org.dhis2.data.database.SqlConstants.POINT;
import static org.dhis2.data.database.SqlConstants.QUESTION_MARK;
import static org.dhis2.data.database.SqlConstants.QUOTE;
import static org.dhis2.data.database.SqlConstants.SELECT;
import static org.dhis2.data.database.SqlConstants.VARIABLE;
import static org.dhis2.data.database.SqlConstants.WHERE;

public class DataSetDetailRepositoryImpl implements DataSetDetailRepository {

    private static final String GET_DATA_SETS = SELECT +
            DataValueModel.TABLE + POINT + DataValueModel.Columns.ORGANISATION_UNIT + COMMA +
            DataValueModel.TABLE + POINT + DataValueModel.Columns.PERIOD + COMMA +
            DataValueModel.TABLE + POINT + DataValueModel.Columns.ATTRIBUTE_OPTION_COMBO +
            FROM + DataValueModel.TABLE +
            JOIN + DataSetDataElementLinkModel.TABLE +
            ON + DataSetDataElementLinkModel.TABLE + POINT + DataSetDataElementLinkModel.Columns.DATA_ELEMENT +
            EQUAL + DataValueModel.TABLE + POINT + DataValueModel.Columns.DATA_ELEMENT +
            WHERE + DataSetDataElementLinkModel.TABLE + POINT + DataSetDataElementLinkModel.Columns.DATA_SET +
            EQUAL + QUESTION_MARK + VARIABLE +
            GROUP_BY + DataValueModel.TABLE + POINT + DataValueModel.Columns.PERIOD + COMMA +
            DataValueModel.TABLE + POINT + DataValueModel.Columns.ORGANISATION_UNIT + COMMA +
            DataValueModel.TABLE + POINT + DataValueModel.Columns.CATEGORY_OPTION_COMBO;

    private static final String DATA_SETS_ORG_UNIT_FILTER = AND + DataValueModel.TABLE + POINT + DataValueModel.Columns.ORGANISATION_UNIT +
            "IN (%s) ";
    private static final String DATA_SETS_PERIOD_FILTER = AND + DataValueModel.TABLE + POINT + DataValueModel.Columns.PERIOD +
            EQUAL + QUESTION_MARK;

    private final BriteDatabase briteDatabase;

    public DataSetDetailRepositoryImpl(BriteDatabase briteDatabase) {
        this.briteDatabase = briteDatabase;
    }


    @NonNull
    @Override
    public Observable<List<OrganisationUnitModel>> orgUnits() {
        String selectOrgUnits = SELECT + ALL + FROM + OrganisationUnitModel.TABLE;
        return briteDatabase.createQuery(OrganisationUnitModel.TABLE, selectOrgUnits)
                .mapToList(OrganisationUnitModel::create);
    }


    private String setOrgUnitFilter(List<String> orgUnits) {
        String orgUnitFilter = "";
        if (orgUnits != null && !orgUnits.isEmpty()) {
            StringBuilder orgUnitUids = new StringBuilder("");
            for (int i = 0; i < orgUnits.size(); i++) {
                orgUnitUids.append(orgUnits.get(i));
                if (i != orgUnits.size() - 1)
                    orgUnitUids.append(",");
            }

            orgUnitFilter = String.format(DATA_SETS_ORG_UNIT_FILTER, orgUnitFilter);
        }
        return String.format(GET_DATA_SETS, orgUnitFilter);
    }

    private String setOrgUnitName(String organisationUnitUid) {
        String orgUnitName = "";
        Cursor orgUnitCursor = briteDatabase.query(
                SELECT + OrganisationUnitModel.TABLE + POINT + OrganisationUnitModel.Columns.DISPLAY_NAME +
                        FROM + OrganisationUnitModel.TABLE +
                        WHERE + OrganisationUnitModel.Columns.UID +
                        EQUAL + QUESTION_MARK, organisationUnitUid);
        if (orgUnitCursor != null && orgUnitCursor.moveToFirst()) {
            orgUnitName = orgUnitCursor.getString(0);
            orgUnitCursor.close();
        }
        return orgUnitName;
    }

    private String setPeriod(String period) {
        String periodName = "";
        Cursor periodCursor = briteDatabase.query(SELECT + PeriodModel.TABLE + POINT + ALL +
                        FROM + PeriodModel.TABLE +
                        WHERE + PeriodModel.TABLE + POINT + PeriodModel.Columns.PERIOD_ID + EQUAL + QUESTION_MARK,
                period);
        if (periodCursor != null && periodCursor.moveToFirst()) {
            PeriodModel periodModel = PeriodModel.create(periodCursor);
            periodName = DateUtils.getInstance().getPeriodUIString(periodModel.periodType(), periodModel.startDate(), Locale.getDefault());
            periodCursor.close();
        }
        return periodName;
    }

    private String setCatCombo(String categoryOptionCombo) {
        String catOptCombName = "";
        Cursor catOptCombCursor = briteDatabase.query(
                SELECT + CategoryOptionComboModel.TABLE + POINT + CategoryOptionComboModel.Columns.DISPLAY_NAME +
                        FROM + CategoryOptionComboModel.TABLE +
                        WHERE + CategoryOptionComboModel.Columns.UID + EQUAL + QUESTION_MARK, categoryOptionCombo);
        if (catOptCombCursor != null && catOptCombCursor.moveToFirst()) {
            catOptCombName = catOptCombCursor.getString(0);
            catOptCombCursor.close();
        }
        return catOptCombName;
    }

    private State setState(Cursor cursor, String period, String organisationUnitUid, String categoryOptionCombo) {
        State state = State.SYNCED;

        Cursor stateCursor = briteDatabase.query(SELECT + DataValueModel.TABLE + POINT + DataValueModel.Columns.STATE +
                        FROM + DataValueModel.TABLE +
                        WHERE + DataValueModel.Columns.PERIOD + EQUAL + QUESTION_MARK +
                        AND + DataValueModel.Columns.ORGANISATION_UNIT + EQUAL + QUESTION_MARK +
                        AND + DataValueModel.Columns.ATTRIBUTE_OPTION_COMBO + EQUAL + QUESTION_MARK +
                        AND + DataValueModel.Columns.STATE + NOT_EQUAL + QUOTE + State.SYNCED + QUOTE,
                period, organisationUnitUid, categoryOptionCombo);
        if (stateCursor != null && stateCursor.moveToFirst()) {
            State errorState = null;
            State toPost = null;
            State toUpdate = null;
            for (int i = 0; i < cursor.getCount(); i++) {
                State stateValue = State.valueOf(cursor.getString(0));
                switch (stateValue) {
                    case ERROR:
                        errorState = State.ERROR;
                        break;
                    case TO_POST:
                        toPost = State.TO_POST;
                        break;
                    case TO_UPDATE:
                        toUpdate = State.TO_UPDATE;
                        break;
                    default:
                        break;
                }
                cursor.moveToNext();
            }
            stateCursor.close();

            if (errorState != null)
                state = errorState;
            else if (toUpdate != null)
                state = toUpdate;
            else if (toPost != null)
                state = toPost;
        }
        return state;
    }

    @Override
    public Flowable<List<DataSetDetailModel>> dataSetGroups(String dataSetUid, List<String> orgUnits, PeriodType selectedPeriodType, int page) {

        String sql = setOrgUnitFilter(orgUnits);

        return briteDatabase.createQuery(DataValueModel.TABLE, sql, dataSetUid)
                .mapToList(cursor -> {
                    String organisationUnitUid = cursor.getString(0);
                    String period = cursor.getString(1);
                    String categoryOptionCombo = cursor.getString(2);

                    String periodName = setPeriod(period);
                    State state = setState(cursor, period, organisationUnitUid, categoryOptionCombo);

                    return DataSetDetailModel.create(organisationUnitUid, categoryOptionCombo,
                            periodName,
                            setOrgUnitName(organisationUnitUid),
                            setCatCombo(categoryOptionCombo),
                            periodName,
                            state);
                }).toFlowable(BackpressureStrategy.LATEST);
    }
}
