package org.dhis2.usescases.programEventDetail;

import org.dhis2.BR;
import org.dhis2.databinding.ItemProgramEventBinding;

import androidx.recyclerview.widget.RecyclerView;

/**
 * QUADRAM. Created by Cristian on 13/02/2018.
 */

public class ProgramEventDetailViewHolder extends RecyclerView.ViewHolder {

    private ItemProgramEventBinding binding;

    public ProgramEventDetailViewHolder(ItemProgramEventBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
    }

    public void bind(ProgramEventDetailContract.ProgramEventDetailPresenter presenter, ProgramEventViewModel event) {
        binding.setVariable(BR.presenter, presenter);
        binding.setVariable(BR.event, event);
        binding.executePendingBindings();

        StringBuilder stringBuilder = new StringBuilder("");
        int valuesSize = event.eventDisplayData().size() > 3 ? 3 : event.eventDisplayData().size();
        for (int i = 0; i < valuesSize; i++) {
            if (event.eventDisplayData().get(i) != null)
                stringBuilder.append(event.eventDisplayData().get(i).val1());
            if (i != valuesSize - 1)
                stringBuilder.append("\n");
        }
        binding.dataValue.setText(stringBuilder);
/*
        disposable.add(presenter.getEventDataValueNew(event)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        values -> {
                            StringBuilder stringBuilder = new StringBuilder("");
                            int valuesSize = values.size() > 3 ? 3 : values.size();
                            for (int i = 0; i < valuesSize; i++) {
                                if (values.get(i) != null)
                                    stringBuilder.append(values.get(i));
                                if (i != valuesSize - 1)
                                    stringBuilder.append("\n");
                            }
                            binding.dataValue.setText(stringBuilder);
                        },
                        Timber::d
                ));*/

        itemView.setOnClickListener(view -> presenter.onEventClick(event.uid(), event.orgUnitUid()));
    }


}
