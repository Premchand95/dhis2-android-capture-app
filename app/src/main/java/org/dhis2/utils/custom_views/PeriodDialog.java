package org.dhis2.utils.custom_views;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import org.dhis2.R;
import org.dhis2.databinding.DialogPeriodBinding;
import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.period.PeriodType;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

/**
 * QUADRAM. Created by ppajuelo on 21/05/2018.
 */

public class PeriodDialog extends DialogFragment {
    DialogPeriodBinding binding;
    private OnDateSet possitiveListener;
    private String title;
    private Date currentDate;
    private PeriodType period;
    private Date minDate;
    private Date maxDate;


    public PeriodDialog() {
        possitiveListener = null;
        title = null;
        currentDate = Calendar.getInstance().getTime();
    }

    public PeriodDialog setPeriod(PeriodType period) {
        this.period = period;
        return this;
    }

    public PeriodDialog setPossitiveListener(OnDateSet listener) {
        this.possitiveListener = listener;
        return this;
    }

    public PeriodDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_period, container, false);

        binding.title.setText(title);
        binding.acceptButton.setOnClickListener(view -> {

            possitiveListener.onDateSet(currentDate);
            dismiss();
        });
        binding.clearButton.setOnClickListener(view -> dismiss());

        binding.periodSubtitle.setText(period.name());
        if (minDate == null || currentDate.after(minDate))
            currentDate = DateUtils.getInstance().getNextPeriod(period, currentDate, 0);
        else if (currentDate.before(minDate))
            currentDate = DateUtils.getInstance().getNextPeriod(period, minDate, 0);
        else
            currentDate = DateUtils.getInstance().getNextPeriod(period, currentDate, 0);

        binding.selectedPeriod.setText(DateUtils.getInstance().getPeriodUIString(period, currentDate, Locale.getDefault()));

        binding.periodBefore.setOnClickListener(view -> {
            previousPeriod();
            checkConstraintDates();


        });
        binding.periodNext.setOnClickListener(view -> {
            nextPeriod();
            checkConstraintDates();


        });

        return binding.getRoot();
    }

    private void nextPeriod() {
        currentDate = DateUtils.getInstance().getNextPeriod(period, currentDate, 1);
        binding.selectedPeriod.setText(DateUtils.getInstance().getPeriodUIString(period, currentDate, Locale.getDefault()));
    }

    private void previousPeriod() {
        currentDate = DateUtils.getInstance().getNextPeriod(period, currentDate, -1);
        binding.selectedPeriod.setText(DateUtils.getInstance().getPeriodUIString(period, currentDate, Locale.getDefault()));
    }

    private void checkConstraintDates() {
        binding.periodBefore.setEnabled(!(minDate != null && minDate.equals(currentDate)));
        binding.periodNext.setEnabled(!(maxDate != null && maxDate.equals(currentDate)));
    }

    public void setMinDate(Date minDate) {
        this.minDate = minDate;
    }

    public PeriodDialog setMaxDate(Date maxDate) {
        this.maxDate = maxDate;
        return this;
    }

    public interface OnDateSet {
        void onDateSet(Date selectedDate);
    }
}
