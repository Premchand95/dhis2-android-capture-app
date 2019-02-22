package org.dhis2.usescases.main.program;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.unnamed.b.atv.model.TreeNode;
import com.unnamed.b.atv.view.AndroidTreeView;

import org.dhis2.BuildConfig;
import org.dhis2.Components;
import org.dhis2.R;
import org.dhis2.databinding.FragmentProgramBinding;
import org.dhis2.usescases.general.FragmentGlobalAbstract;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.HelpManager;
import org.dhis2.utils.Period;
import org.dhis2.utils.custom_views.RxDateDialog;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import io.reactivex.functions.Consumer;
import me.toptas.fancyshowcase.DismissListener;
import me.toptas.fancyshowcase.FancyShowCaseView;
import me.toptas.fancyshowcase.FocusShape;
import timber.log.Timber;

import static org.dhis2.utils.Period.DAILY;
import static org.dhis2.utils.Period.MONTHLY;
import static org.dhis2.utils.Period.NONE;
import static org.dhis2.utils.Period.WEEKLY;
import static org.dhis2.utils.Period.YEARLY;

/**
 * Created by ppajuelo on 18/10/2017.f
 */

public class ProgramFragment extends FragmentGlobalAbstract implements ProgramContract.ProgramView, OrgUnitInterface {

    private FragmentProgramBinding binding;

    @Inject
    ProgramContract.ProgramPresenter presenter;

    private Period currentPeriod = NONE;
    private StringBuilder orgUnitFilter = new StringBuilder();

    private AndroidTreeView treeView;

    private Date chosenDateDay = new Date();
    private ArrayList<Date> chosenDateWeek = new ArrayList<>();
    private ArrayList<Date> chosenDateMonth = new ArrayList<>();
    private ArrayList<Date> chosenDateYear = new ArrayList<>();
    private SimpleDateFormat monthFormat = new SimpleDateFormat("MMM-yyyy", Locale.getDefault());
    private SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
    private TreeNode treeNode;
    private Context context;

    //-------------------------------------------
    //region LIFECYCLE


    public FragmentProgramBinding getBinding() {
        return binding;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        if (getActivity() != null) {
            ((Components) getActivity().getApplicationContext()).userComponent()
                    .plus(new ProgramModule()).inject(this);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_program, container, false);
        binding.setPresenter(presenter);
        chosenDateWeek.add(new Date());
        chosenDateMonth.add(new Date());
        chosenDateYear.add(new Date());
        binding.programRecycler.setAdapter(new ProgramModelAdapter(presenter, currentPeriod));
        binding.programRecycler.addItemDecoration(new DividerItemDecoration(getAbstracContext(), DividerItemDecoration.VERTICAL));
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.init(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        presenter.dispose();
        binding.treeViewContainer.removeAllViews();
        treeView = null;
    }

    //endregion

    public void setOrgUnitFilter(StringBuilder orgUnitFilter) {
        this.orgUnitFilter = orgUnitFilter;
    }

    @SuppressLint({"CheckResult", "RxLeakedSubscription"})
    @Override
    public void showRageDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setMinimalDaysInFirstWeek(7);

        if (currentPeriod != DAILY && currentPeriod != NONE) {
            setUpChooser();
        } else if (currentPeriod == DAILY) {
            setUpDailyPeriodChooser(calendar);
        }
    }

    @SuppressLint({"RxLeakedSubscription", "CheckResult"})
    private void setUpChooser() {
        String week = getString(R.string.week);
        SimpleDateFormat weeklyFormat = new SimpleDateFormat("'" + week + "' w", Locale.getDefault());
        new RxDateDialog(getAbstractActivity(), currentPeriod).create().show().subscribe(selectedDates -> {
            if (!selectedDates.isEmpty()) {
                displaySelection(selectedDates, weeklyFormat);
            } else {
                displayEmptySelection(weeklyFormat);
            }
        }, Timber::d);
    }

    private void displaySelection(List<Date> selectedDates, SimpleDateFormat weeklyFormat) {
        String textToShow;
        if (currentPeriod == WEEKLY) {
            textToShow = weeklyFormat.format(selectedDates.get(0)) + ", " + yearFormat.format(selectedDates.get(0));
            chosenDateWeek = (ArrayList<Date>) selectedDates;
            if (selectedDates.size() > 1)
                textToShow += "... " /*+ weeklyFormat.format(selectedDates.get(1))*/;
        } else if (currentPeriod == MONTHLY) {
            String dateFormatted = monthFormat.format(selectedDates.get(0));
            textToShow = dateFormatted.substring(0, 1).toUpperCase() + dateFormatted.substring(1);
            chosenDateMonth = (ArrayList<Date>) selectedDates;
            if (selectedDates.size() > 1)
                textToShow += "... " /*+ monthFormat.format(selectedDates.get(1))*/;
        } else {
            textToShow = yearFormat.format(selectedDates.get(0));
            chosenDateYear = (ArrayList<Date>) selectedDates;
            if (selectedDates.size() > 1)
                textToShow += "... " /*+ yearFormat.format(selectedDates.get(1))*/;

        }
        binding.buttonPeriodText.setText(textToShow);
        getSelectedPrograms((ArrayList<Date>) selectedDates, currentPeriod, orgUnitFilter.toString());

    }

    private void displayEmptySelection(SimpleDateFormat weeklyFormat) {
        ArrayList<Date> date = new ArrayList<>();
        date.add(new Date());

        String text = "";

        switch (currentPeriod) {
            case WEEKLY:
                text = weeklyFormat.format(date.get(0)) + ", " + yearFormat.format(date.get(0));
                chosenDateWeek = date;
                break;
            case MONTHLY:
                String dateFormatted = monthFormat.format(date.get(0));
                text = dateFormatted.substring(0, 1).toUpperCase() + dateFormatted.substring(1);
                chosenDateMonth = date;
                break;
            case YEARLY:
                text = yearFormat.format(date.get(0));
                chosenDateYear = date;
                break;
            default:
                break;
        }
        binding.buttonPeriodText.setText(text);
        getSelectedPrograms(date, currentPeriod, orgUnitFilter.toString());
    }

    private void setUpDailyPeriodChooser(Calendar calendar) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(chosenDateDay);
        DatePickerDialog pickerDialog;
        pickerDialog = new DatePickerDialog(context, (datePicker, year, monthOfYear, dayOfMonth) -> {
            calendar.set(year, monthOfYear, dayOfMonth);
            Date[] dates = DateUtils.getInstance().getDateFromDateAndPeriod(calendar.getTime(), currentPeriod);
            ArrayList<Date> day = new ArrayList<>();
            day.add(dates[0]);
            getSelectedPrograms(day, currentPeriod, orgUnitFilter.toString());
            binding.buttonPeriodText.setText(DateUtils.getInstance().formatDate(dates[0]));
            chosenDateDay = dates[0];
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        pickerDialog.show();
    }

    public Period getCurrentPeriod() {
        return currentPeriod;
    }

    private String getDailyTextToShow() {
        String textToShow = "";
        ArrayList<Date> datesD = new ArrayList<>();
        datesD.add(chosenDateDay);
        if (!datesD.isEmpty())
            textToShow = DateUtils.getInstance().formatDate(datesD.get(0));
        if (!datesD.isEmpty() && datesD.size() > 1) {
            textToShow += "... ";
        }
        getSelectedPrograms(datesD, currentPeriod, orgUnitFilter.toString());
        return textToShow;
    }

    private String getWeeklyTextToShow() {
        String textToShow = "";
        if (!chosenDateWeek.isEmpty()) {
            String week = getString(R.string.week);
            SimpleDateFormat weeklyFormat = new SimpleDateFormat("'" + week + "' w", Locale.getDefault());
            textToShow = weeklyFormat.format(chosenDateWeek.get(0)) + ", " + yearFormat.format(chosenDateWeek.get(0));
        }
        if (!chosenDateWeek.isEmpty() && chosenDateWeek.size() > 1) textToShow += "... ";
        getSelectedPrograms(chosenDateWeek, currentPeriod, orgUnitFilter.toString());
        return textToShow;
    }

    private String getMonthlyTextToShow() {
        String textToShow = "";
        if (!chosenDateMonth.isEmpty()) {
            String dateFormatted = monthFormat.format(chosenDateMonth.get(0));
            textToShow = dateFormatted.substring(0, 1).toUpperCase() + dateFormatted.substring(1);
        }
        if (!chosenDateMonth.isEmpty() && chosenDateMonth.size() > 1) {
            textToShow += "... ";
        }
        getSelectedPrograms(chosenDateMonth, currentPeriod, orgUnitFilter.toString());
        return textToShow;
    }

    private String getYearlyTextToShow() {
        String textToShow = "";
        if (!chosenDateYear.isEmpty())
            textToShow = yearFormat.format(chosenDateYear.get(0));
        if (!chosenDateYear.isEmpty() && chosenDateYear.size() > 1) {
            textToShow += "... ";
        }
        getSelectedPrograms(chosenDateYear, currentPeriod, orgUnitFilter.toString());
        return textToShow;
    }

    private String getPeriodTextToShow() {
        String textToShow = null;
        switch (currentPeriod) {
            case NONE:
                getSelectedPrograms(null, currentPeriod, orgUnitFilter.toString());
                textToShow = getString(R.string.period);
                break;
            case DAILY:
                textToShow = getDailyTextToShow();
                break;
            case WEEKLY:
                textToShow = getWeeklyTextToShow();
                break;
            case MONTHLY:
                textToShow = getMonthlyTextToShow();
                break;
            case YEARLY:
                textToShow = getYearlyTextToShow();
                break;
            default:
                break;
        }
        return textToShow;
    }

    private Drawable getPeriodDrawable() {
        switch (currentPeriod) {
            case NONE:
                currentPeriod = DAILY;
                return ContextCompat.getDrawable(context, R.drawable.ic_view_day);
            case DAILY:
                currentPeriod = WEEKLY;
                return ContextCompat.getDrawable(context, R.drawable.ic_view_week);
            case WEEKLY:
                currentPeriod = MONTHLY;
                return ContextCompat.getDrawable(context, R.drawable.ic_view_month);
            case MONTHLY:
                currentPeriod = YEARLY;
                return ContextCompat.getDrawable(context, R.drawable.ic_view_year);
            case YEARLY:
                currentPeriod = NONE;
                return ContextCompat.getDrawable(context, R.drawable.ic_view_none);
            default:
                return null;
        }
    }

    @Override
    public void showTimeUnitPicker() {

        Drawable drawable = getPeriodDrawable();
        String textToShow = getPeriodTextToShow();

        ((ProgramModelAdapter) binding.programRecycler.getAdapter()).setCurrentPeriod(currentPeriod);
        binding.buttonTime.setImageDrawable(drawable);
        binding.buttonPeriodText.setText(textToShow);
    }

    @Override
    public void setUpRecycler() {
        presenter.init(this);
    }

    @Override
    public void getSelectedPrograms(ArrayList<Date> dates, Period period, String orgUnitQuery) {
        if (dates != null)
            if (orgUnitQuery.isEmpty())
                presenter.getProgramsWithDates(dates, period);
            else
                presenter.getProgramsOrgUnit(dates, period, orgUnitQuery);
        else
            presenter.getAllPrograms(orgUnitQuery);
    }

    @Override
    public Consumer<List<ProgramViewModel>> swapProgramModelData() {
        return programs -> {
            binding.programProgress.setVisibility(View.GONE);
            binding.emptyView.setVisibility(programs.isEmpty() ? View.VISIBLE : View.GONE);
            ((ProgramModelAdapter) binding.programRecycler.getAdapter()).setData(programs);

            setTutorial();
        };
    }

    @Override
    public void renderError(String message) {
        if (getActivity() != null)
            new AlertDialog.Builder(getActivity())
                    .setPositiveButton(android.R.string.ok, null)
                    .setTitle(getString(R.string.error))
                    .setMessage(message)
                    .show();
    }

    @Override
    public void addTree(TreeNode treeNode) {
        this.treeNode = treeNode;
        binding.treeViewContainer.removeAllViews();
        binding.orgUnitApply.setOnClickListener(view -> apply());
        binding.orgUnitCancel.setOnClickListener(view -> {
            binding.drawerLayout.closeDrawer(GravityCompat.END);
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        });
        binding.orgUnitAll.setOnClickListener(view -> {
            treeView.selectAll(false);
            for (TreeNode node : treeView.getSelected()) {
                ((OrgUnitHolder) node.getViewHolder()).check();
            }
        });

        binding.orgUnitUnselectAll.setOnClickListener(view -> {
            for (TreeNode node : treeView.getSelected()) {
                ((OrgUnitHolder) node.getViewHolder()).uncheck();
                ((OrgUnitHolder) node.getViewHolder()).update();
            }
            treeView.deselectAll();

        });
        treeView = new AndroidTreeView(context, treeNode);

        treeView.setDefaultContainerStyle(R.style.TreeNodeStyle, false);
        treeView.setSelectionModeEnabled(true);
        treeView.setUseAutoToggle(false);

        binding.treeViewContainer.addView(treeView.getView());
        if (presenter.getOrgUnits().size() < 25)
            treeView.expandAll();

        treeView.setDefaultNodeClickListener((node, value) -> {
            if ((treeView.getSelected().size() == 1 && !node.isSelected()) ||
                    (treeView.getSelected().size() > 1)) {
                binding.buttonOrgUnit.setText(String.format(getString(R.string.org_unit_filter), treeView.getSelected().size()));
            }
        });

        binding.buttonOrgUnit.setText(String.format(getString(R.string.org_unit_filter), treeView.getSelected().size()));
    }


    public boolean areAllOrgUnitsSelected() {
        return treeNode != null && presenter.getOrgUnits().size() == treeView.getSelected().size();
    }

    @Override
    public void openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.END);
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
    }

    @Override
    public ArrayList<Date> getChosenDateWeek() {
        return chosenDateWeek;
    }

    @Override
    public ArrayList<Date> getChosenDateMonth() {
        return chosenDateMonth;
    }

    @Override
    public ArrayList<Date> getChosenDateYear() {
        return chosenDateYear;
    }

    @Override
    public Date getChosenDateDay() {
        return chosenDateDay;
    }


    @Override
    public void apply() {
        binding.drawerLayout.closeDrawers();
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        orgUnitFilter = new StringBuilder();
        for (int i = 0; i < treeView.getSelected().size(); i++) {
            orgUnitFilter.append("'");
            orgUnitFilter.append(((OrganisationUnitModel) treeView.getSelected().get(i).getValue()).uid());
            orgUnitFilter.append("'");
            if (i < treeView.getSelected().size() - 1)
                orgUnitFilter.append(", ");
        }

        binding.buttonOrgUnit.setText(String.format(getString(R.string.org_unit_filter), treeView.getSelected().size()));

        switch (currentPeriod) {
            case NONE:
                getSelectedPrograms(null, currentPeriod, orgUnitFilter.toString());
                break;
            case DAILY:
                ArrayList<Date> datesD = new ArrayList<>();
                datesD.add(chosenDateDay);
                getSelectedPrograms(datesD, currentPeriod, orgUnitFilter.toString());
                break;
            case WEEKLY:
                getSelectedPrograms(chosenDateWeek, currentPeriod, orgUnitFilter.toString());
                break;
            case MONTHLY:
                getSelectedPrograms(chosenDateMonth, currentPeriod, orgUnitFilter.toString());
                break;
            case YEARLY:
                getSelectedPrograms(chosenDateYear, currentPeriod, orgUnitFilter.toString());
                break;
        }
        ((ProgramModelAdapter) binding.programRecycler.getAdapter()).setCurrentPeriod(currentPeriod);
    }

    @Override
    public void setTutorial() {
        SharedPreferences prefs = getAbstracContext().getSharedPreferences(
                Constants.SHARE_PREFS, Context.MODE_PRIVATE);

        if (isAdded() && getAbstractActivity() != null) {
            new Handler().postDelayed(() -> {
                FancyShowCaseView tuto1 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_1))
                        .closeOnTouch(true)
                        .build();
                FancyShowCaseView tuto2 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_2))
                        .closeOnTouch(true)
                        .build();

                FancyShowCaseView tuto3 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_3))
                        .focusOn(getAbstractActivity().findViewById(R.id.filter))
                        .closeOnTouch(true)
                        .dismissListener(new DismissListener() {
                            @Override
                            public void onDismiss(String id) {
                                if (getAbstractActivity().findViewById(R.id.filter_layout).getVisibility() == View.GONE)
                                    getAbstractActivity().findViewById(R.id.filter).performClick();
                            }

                            @Override
                            public void onSkipped(String id) {
                                // do nothing
                            }
                        })
                        .build();

                FancyShowCaseView tuto4 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_4))
                        .focusOn(binding.periodLayout)
                        .focusShape(FocusShape.ROUNDED_RECTANGLE)
                        .closeOnTouch(true)
                        .build();

                FancyShowCaseView tuto5 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_5))
                        .focusOn(binding.buttonOrgUnit)
                        .focusShape(FocusShape.ROUNDED_RECTANGLE)
                        .closeOnTouch(true)
                        .build();

                FancyShowCaseView tuto6 = new FancyShowCaseView.Builder(getAbstractActivity())
                        .title(getString(R.string.tuto_main_6))
                        .focusOn(getAbstractActivity().findViewById(R.id.menu))
                        .closeOnTouch(true)
                        .dismissListener(new DismissListener() {
                            @Override
                            public void onDismiss(String id) {
                                // do nothing
                            }

                            @Override
                            public void onSkipped(String id) {
                                // do nothing
                            }
                        })
                        .build();

                ArrayList<FancyShowCaseView> steps = new ArrayList<>();
                steps.add(tuto1);
                steps.add(tuto2);
                steps.add(tuto3);
                steps.add(tuto4);
                steps.add(tuto5);
                steps.add(tuto6);


                HelpManager.getInstance().setScreenHelp(getClass().getName(), steps);

                if (!prefs.getBoolean("TUTO_SHOWN", false) && !BuildConfig.DEBUG) {
                    HelpManager.getInstance().showHelp();
                    prefs.edit().putBoolean("TUTO_SHOWN", true).apply();
                }

            }, 500);
        }
    }
}