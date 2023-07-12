package de.baumann.browser.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import de.baumann.browser.R;
import de.baumann.browser.objects.CustomRedirect;

public class AdapterCustomRedirect extends RecyclerView.Adapter<RedirectsViewHolder> {
    final private ArrayList<CustomRedirect> redirects;

    public AdapterCustomRedirect(ArrayList<CustomRedirect> redirects) {
        super();
        this.redirects = redirects;
    }

    @NonNull
    @Override
    public RedirectsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.custom_redirects_row, parent, false);

        return new RedirectsViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull RedirectsViewHolder holder, int position) {
        CustomRedirect current = redirects.get(position);
        TextView source = holder.itemView.findViewById(R.id.redirect_source);
        TextView target = holder.itemView.findViewById(R.id.redirect_target);
        ImageView remove = holder.itemView.findViewById(R.id.remove_redirect);
        source.setText(current.getSource());
        target.setText(current.getTarget());
        remove.setOnClickListener((iV) -> {
            redirects.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, getItemCount());
        });
    }

    @Override
    public int getItemCount() {
        return redirects.size();
    }

    public ArrayList<CustomRedirect> getRedirects() {
        return redirects;
    }

    public void addRedirect(CustomRedirect redirect) {
        redirects.add(redirect);
        notifyItemInserted(getItemCount() - 1);
    }
}

