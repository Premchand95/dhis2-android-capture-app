package org.dhis2.data.forms.dataentry;

import android.content.ContentValues;
import android.database.Cursor;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.tuples.Pair;
import org.dhis2.data.user.UserRepository;
import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.common.BaseIdentifiableObject;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.enrollment.EnrollmentModel;
import org.hisp.dhis.android.core.event.EventModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValueModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityDataValueModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceModel;
import org.hisp.dhis.android.core.user.UserCredentialsModel;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import static org.dhis2.data.database.SqlConstants.ALL;
import static org.dhis2.data.database.SqlConstants.AND;
import static org.dhis2.data.database.SqlConstants.EQUAL;
import static org.dhis2.data.database.SqlConstants.FROM;
import static org.dhis2.data.database.SqlConstants.JOIN;
import static org.dhis2.data.database.SqlConstants.LIMIT_1;
import static org.dhis2.data.database.SqlConstants.NOT_EQUAL;
import static org.dhis2.data.database.SqlConstants.ON;
import static org.dhis2.data.database.SqlConstants.POINT;
import static org.dhis2.data.database.SqlConstants.QUESTION_MARK;
import static org.dhis2.data.database.SqlConstants.QUOTE;
import static org.dhis2.data.database.SqlConstants.SELECT;
import static org.dhis2.data.database.SqlConstants.WHERE;
import static org.dhis2.data.forms.dataentry.DataEntryStore.valueType.ATTR;
import static org.dhis2.data.forms.dataentry.DataEntryStore.valueType.DATA_ELEMENT;

public final class DataValueStore implements DataEntryStore {
    private static final String SELECT_EVENT =
            SELECT + ALL + FROM + EventModel.TABLE +
                    WHERE + EventModel.Columns.UID +
                    EQUAL + QUESTION_MARK +
                    AND + EventModel.Columns.STATE + NOT_EQUAL + QUOTE + State.TO_DELETE + QUOTE + LIMIT_1;

    private static final String ENROLLMENT_QUERY = SELECT + EnrollmentModel.TABLE + POINT + EnrollmentModel.Columns.TRACKED_ENTITY_INSTANCE +
            FROM + TrackedEntityAttributeValueModel.TABLE +
            JOIN + EnrollmentModel.TABLE +
            ON + EnrollmentModel.TABLE + POINT + EnrollmentModel.Columns.TRACKED_ENTITY_INSTANCE +
            EQUAL + TrackedEntityAttributeValueModel.TABLE + POINT + TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_INSTANCE +
            WHERE + TrackedEntityAttributeValueModel.TABLE + POINT + TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_ATTRIBUTE +
            EQUAL + QUESTION_MARK;

    @NonNull
    private final BriteDatabase briteDatabase;
    @NonNull
    private final Flowable<UserCredentialsModel> userCredentials;

    @NonNull
    private final String eventUid;

    public DataValueStore(@NonNull BriteDatabase briteDatabase,
                          @NonNull UserRepository userRepository,
                          @NonNull String eventUid) {
        this.briteDatabase = briteDatabase;
        this.eventUid = eventUid;

        // we want to re-use results of the user credentials query
        this.userCredentials = userRepository.credentials()
                .cacheWithInitialCapacity(1);
    }

    @NonNull
    @Override
    public Flowable<Long> save(@NonNull String uid, @Nullable String value) {
        return userCredentials
                .map(userCredentialsModel -> Pair.create(userCredentialsModel, getValueType(uid)))
                .filter(userCredentialAndType -> {
                    String currentValue = currentValue(uid, userCredentialAndType.val1());
                    return !Objects.equals(currentValue, value);
                })
                .switchMap(userCredentialAndType -> {
                    if (value == null)
                        return Flowable.just(delete(uid, userCredentialAndType.val1()));

                    long updated = update(uid, value, userCredentialAndType.val1());
                    if (updated > 0) {
                        return Flowable.just(updated);
                    }

                    return Flowable.just(insert(uid, value, userCredentialAndType.val0().username(), userCredentialAndType.val1()));
                })
                .switchMap(this::updateEvent);
    }


    private long update(@NonNull String uid, @Nullable String value, valueType valueType) {
        ContentValues dataValue = new ContentValues();
        if (valueType == DATA_ELEMENT) {
            // renderSearchResults time stamp
            dataValue.put(TrackedEntityDataValueModel.Columns.LAST_UPDATED,
                    BaseIdentifiableObject.DATE_FORMAT.format(Calendar.getInstance().getTime()));
            if (value == null) {
                dataValue.putNull(TrackedEntityDataValueModel.Columns.VALUE);
            } else {
                dataValue.put(TrackedEntityDataValueModel.Columns.VALUE, value);
            }

            // ToDo: write test cases for different events
            return (long) briteDatabase.update(TrackedEntityDataValueModel.TABLE, dataValue,
                    TrackedEntityDataValueModel.Columns.DATA_ELEMENT + EQUAL + QUESTION_MARK + AND +
                            TrackedEntityDataValueModel.Columns.EVENT + EQUAL + QUESTION_MARK, uid, eventUid);
        } else {
            dataValue.put(TrackedEntityAttributeValueModel.Columns.LAST_UPDATED,
                    BaseIdentifiableObject.DATE_FORMAT.format(Calendar.getInstance().getTime()));
            if (value == null) {
                dataValue.putNull(TrackedEntityAttributeValueModel.Columns.VALUE);
            } else {
                dataValue.put(TrackedEntityAttributeValueModel.Columns.VALUE, value);
            }

            Cursor enrollmentCursor = briteDatabase.query(ENROLLMENT_QUERY, uid);
            String teiUid = "";
            if (enrollmentCursor != null && enrollmentCursor.moveToFirst()) {
                teiUid = enrollmentCursor.getString(0);
            }
            return (long) briteDatabase.update(TrackedEntityAttributeValueModel.TABLE, dataValue,
                    TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_ATTRIBUTE + EQUAL + QUESTION_MARK + AND +
                            TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_INSTANCE + EQUAL + QUESTION_MARK,
                    uid, teiUid);
        }
    }

    private valueType getValueType(@Nonnull String uid) {
        Cursor attrCursor = briteDatabase.query(SELECT +
                TrackedEntityAttributeModel.TABLE + POINT + TrackedEntityAttributeModel.Columns.UID +
                FROM + TrackedEntityAttributeModel.TABLE +
                WHERE + TrackedEntityAttributeModel.TABLE + POINT + TrackedEntityAttributeModel.Columns.UID +
                EQUAL + QUESTION_MARK, uid);
        String attrUid = null;
        if (attrCursor != null && attrCursor.moveToFirst()) {
            attrUid = attrCursor.getString(0);
        }

        return attrUid != null ? ATTR : valueType.DATA_ELEMENT;
    }

    private String currentValue(@NonNull String uid, valueType valueType) {
        Cursor cursor;
        if (valueType == DATA_ELEMENT)
            cursor = briteDatabase.query("SELECT TrackedEntityDataValue.value FROM TrackedEntityDataValue " +
                    "WHERE dataElement = ? AND event = ?", uid, eventUid);
        else
            cursor = briteDatabase.query("SELECT TrackedEntityAttributeValue.value FROM TrackedEntityAttributeValue " +
                    "JOIN Enrollment ON Enrollment.trackedEntityInstance = TrackedEntityAttributeValue.trackedEntityInstance " +
                    "JOIN Event ON Event.enrollment = Enrollment.uid " +
                    "WHERE TrackedEntityAttributeValue.trackedEntityAttribute = ? " +
                    "AND Event.uid = ?", uid, eventUid);

        if (cursor != null && cursor.moveToFirst()) {
            String value = cursor.getString(0);
            cursor.close();
            return value;
        } else
            return "";
    }

    private long insert(@NonNull String uid, @Nullable String value, @NonNull String storedBy, valueType valueType) {
        Date created = Calendar.getInstance().getTime();
        if (valueType == DATA_ELEMENT) {
            TrackedEntityDataValueModel dataValueModel =
                    TrackedEntityDataValueModel.builder()
                            .created(created)
                            .lastUpdated(created)
                            .dataElement(uid)
                            .event(eventUid)
                            .value(value)
                            .storedBy(storedBy)
                            .build();
            return briteDatabase.insert(TrackedEntityDataValueModel.TABLE,
                    dataValueModel.toContentValues());
        } else {
            Cursor enrollmentCursor = briteDatabase.query(ENROLLMENT_QUERY, uid);
            String teiUid = "";
            if (enrollmentCursor != null && enrollmentCursor.moveToFirst()) {
                teiUid = enrollmentCursor.getString(0);
            }
            TrackedEntityAttributeValueModel attributeValueModel =
                    TrackedEntityAttributeValueModel.builder()
                            .created(created)
                            .lastUpdated(created)
                            .trackedEntityAttribute(uid)
                            .trackedEntityInstance(teiUid)
                            .build();
            return briteDatabase.insert(TrackedEntityAttributeValueModel.TABLE,
                    attributeValueModel.toContentValues());
        }
    }

    private long delete(@NonNull String uid, valueType valueType) {
        if (valueType == DATA_ELEMENT)
            return (long) briteDatabase.delete(TrackedEntityDataValueModel.TABLE,
                    TrackedEntityDataValueModel.Columns.DATA_ELEMENT + " = ? AND " +
                            TrackedEntityDataValueModel.Columns.EVENT + " = ?",
                    uid, eventUid);
        else {
            Cursor enrollmentCursor = briteDatabase.query(ENROLLMENT_QUERY, uid);
            String teiUid = "";
            if (enrollmentCursor != null && enrollmentCursor.moveToFirst()) {
                teiUid = enrollmentCursor.getString(0);
            }
            return (long) briteDatabase.delete(TrackedEntityAttributeValueModel.TABLE,
                    TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_ATTRIBUTE + " = ? AND " +
                            TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_INSTANCE + " = ? ",
                    uid, teiUid);
        }
    }

    private Flowable<Long> updateEvent(long status) {
        return briteDatabase.createQuery(EventModel.TABLE, SELECT_EVENT, eventUid)
                .mapToOne(EventModel::create).take(1).toFlowable(BackpressureStrategy.LATEST)
                .switchMap(eventModel -> {
                    if (State.SYNCED.equals(eventModel.state()) || State.TO_DELETE.equals(eventModel.state()) ||
                            State.ERROR.equals(eventModel.state())) {
                        updateSyncedDeletedErrorEvent(eventModel);
                    } else {
                        updateEvent(eventModel);
                    }

                    return Flowable.just(status);
                });
    }

    private void updateEvent(EventModel eventModel) {
        if (eventModel.enrollment() != null) {
            Cursor teiCursor = briteDatabase.query("SELECT TrackedEntityInstance .* FROM TrackedEntityInstance " +
                    "JOIN Enrollment ON Enrollment.trackedEntityInstance = TrackedEntityInstance.uid WHERE Enrollment.uid = ?", eventModel.enrollment());
            if (teiCursor != null && teiCursor.moveToFirst()) {
                TrackedEntityInstanceModel tei = TrackedEntityInstanceModel.create(teiCursor);
                ContentValues cv = tei.toContentValues();
                cv.put(TrackedEntityInstanceModel.Columns.STATE, tei.state() == State.TO_POST ? State.TO_POST.name() : State.TO_UPDATE.name());
                cv.put(TrackedEntityInstanceModel.Columns.LAST_UPDATED, DateUtils.databaseDateFormat().format(Calendar.getInstance().getTime()));

                briteDatabase.update(TrackedEntityInstanceModel.TABLE, cv, "uid = ?", tei.uid());
            }
        }
    }

    private void updateSyncedDeletedErrorEvent(EventModel eventModel) {
        ContentValues values = eventModel.toContentValues();
        values.put(EventModel.Columns.STATE, State.TO_UPDATE.toString());

        if (briteDatabase.update(EventModel.TABLE, values,
                EventModel.Columns.UID + " = ?", eventUid == null ? "" : eventUid) <= 0) {

            throw new IllegalStateException(String.format(Locale.US, "Event=[%s] " +
                    "has not been successfully updated", eventUid));
        }
    }

}
