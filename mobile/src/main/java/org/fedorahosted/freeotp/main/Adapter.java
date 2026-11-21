/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2018  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp.main;

import android.content.Context;
import android.os.Handler;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.UserNotAuthenticatedException;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.fedorahosted.freeotp.Code;
import org.fedorahosted.freeotp.TokenIcon;
import org.fedorahosted.freeotp.R;
import org.fedorahosted.freeotp.Token;
import org.fedorahosted.freeotp.TokenPersistence;
import org.fedorahosted.freeotp.data.TokenRepository;
import org.fedorahosted.freeotp.utils.SelectableAdapter;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

/** RecyclerView adapter over {@link TokenRepository}; maps list positions to token UUIDs. */
public class Adapter extends SelectableAdapter<ViewHolder> implements ViewHolder.EventListener {
    private static final String LOGTAG = "Adapter";

    private final LongSparseArray<Code> mActive = new LongSparseArray<>();
    private final Handler mHandler = new Handler();
    private final TokenRepository mRepository;
    private Context mContext;

    public Adapter(Context context, EventListener listener) throws GeneralSecurityException, IOException {
        super(listener);
        setHasStableIds(true);
        mRepository = new TokenRepository(context);
    }

    private String uuidAt(int position) {
        return mRepository.idAt(position);
    }

    private Token tokenAt(int position) {
        return mRepository.get(uuidAt(position));
    }

    @Override
    public long getItemId(int position) {
        return UUID.fromString(uuidAt(position)).getMostSignificantBits();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater li = LayoutInflater.from(parent.getContext());
        View v = li.inflate(R.layout.token, parent, false);
        return new ViewHolder(v, this);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.reset();
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Token token = tokenAt(position);
        TokenIcon token_icon = new TokenIcon(token, mContext);
        Pair <Integer, String> image = token_icon.mImage;
        Log.i(LOGTAG, String.format("Bind to view token [%s][%s]", token.getIssuer(), token.getLabel()));
        holder.bind(token, token_icon.mColor, image.first, image.second,
                mActive.get(getItemId(position)), isSelected(position), token.getType());
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mContext = recyclerView.getContext();
    }

    @Override
    public int getItemCount() {
        return mRepository.getSize();
    }

    public int add(SecretKey key, Token token) throws GeneralSecurityException, IOException {
        mRepository.add(key, token);
        int position = mRepository.getSize() - 1;
        notifyItemInserted(position);
        return position;
    }

    public void delete(int position) throws GeneralSecurityException, IOException {
        mRepository.delete(uuidAt(position));
        notifyItemRemoved(position);
    }

    public void move(int fromPosition, int toPosition) {
        if (mRepository.move(fromPosition, toPosition))
            notifyItemMoved(fromPosition, toPosition);
    }

    public void sortByMostRecentlyUsed() {
        if (mRepository.sortByMostRecentlyUsed())
            notifyDataSetChanged();
    }

    @Override
    public boolean onSelectionToggled(ViewHolder holder) {
        boolean selected = !isSelected(holder.getAdapterPosition());
        setSelected(holder.getAdapterPosition(), selected);
        return selected;
    }

    public void restoreTokens(String pwd) throws TokenPersistence.BadPasswordException {
        int start = mRepository.getSize();
        List<String> restored = mRepository.restore(pwd);
        int inserted = mRepository.getSize() - start;
        if (restored.size() > inserted)
            notifyItemRangeChanged(0, start);
        if (inserted > 0)
            notifyItemRangeInserted(start, inserted);
    }

    public Code getCode(int position)
            throws UserNotAuthenticatedException, KeyPermanentlyInvalidatedException {
        Code code = mRepository.getCode(uuidAt(position));

        final Long id = getItemId(position);
        mActive.put(id, code);

        // Minimum delay to prevent rapid rescheduling when timeLeft() is 0 or near-zero
        final long MIN_DELAY_MS = 500;
        long initialDelay = Math.max(code.timeLeft(), MIN_DELAY_MS);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Code code = mActive.get(id);
                if (code == null)
                    return;
                if (!code.isValid())
                    mActive.remove(id);
                else {
                    long delay = Math.max(code.timeLeft(), MIN_DELAY_MS);
                    mHandler.postDelayed(this, delay);
                }
            }
        }, initialDelay);

        return code;
    }

    public Pair<String, String> getLabel(int position) {
        Token token = tokenAt(position);
        return new Pair<>(token.getLabel(), token.getIssuer());
    }

    public TokenIcon getTokenIcon(int position) {
        return new TokenIcon(tokenAt(position), mContext);
    }

    public void setLabel(int position, String account, String issuer) throws IOException {
        mRepository.setLabel(uuidAt(position), account, issuer);
    }

    @Override
    public void onActivated(ViewHolder holder) {
    }

    @Override
    public void onShare(String code) {
    }
}
