package org.mushare.httper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.mushare.httper.dialog.ClearRequestDialog;
import org.mushare.httper.dialog.RequestRawBodyDialog;
import org.mushare.httper.entity.DaoSession;
import org.mushare.httper.entity.RequestRecord;
import org.mushare.httper.entity.RequestRecordDao;
import org.mushare.httper.utils.MyApp;
import org.mushare.httper.utils.MyPair;
import org.mushare.httper.utils.RequestSettingDataUtils;
import org.mushare.httper.view.MyStickyHeader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.grantland.widget.AutofitHelper;

import static android.app.Activity.RESULT_OK;
import static org.mushare.httper.utils.HttpUtils.jsonArrayToPairList;
import static org.mushare.httper.utils.HttpUtils.pairListToJSONArray;

/**
 * Created by dklap on 5/22/2017.
 */

public class MainFragment extends Fragment {
    final int HISTORY_CODE = 0;
    FastItemAdapter<IItem> adapter;
    MyStickyHeader stickyHeader;
    RequestRecordDao requestRecordDao;

    Spinner spinnerMethod;
    Spinner spinnerHttp;
    EditText editTextUrl;
    String body;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        DaoSession daoSession = ((MyApp) getActivity().getApplication()).getDaoSession();
        requestRecordDao = daoSession.getRequestRecordDao();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_main, container, false);
        spinnerMethod = (Spinner) view.findViewById(R.id.spinnerMethods);
        spinnerHttp = (Spinner) view.findViewById(R.id.spinnerHttp);
        editTextUrl = (EditText) view.findViewById(R.id.editTextUrl);
        AutofitHelper.create(editTextUrl).setMinTextSize(TypedValue.COMPLEX_UNIT_SP, 14)
                .setPrecision(0.1f);
        final Button buttonSend = (Button) view.findViewById(R.id.buttonSend);

        editTextUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && s.toString().matches("^" +
                        // user:pass authentication
                        "(?:\\S+(?::\\S*)?@)?" +
                        "(?:" +
                        // IP address exclusion
                        // private & local networks
                        "(?!(?:10|127)(?:\\.\\d{1,3}){3})" +
                        "(?!(?:169\\.254|192\\.168)(?:\\.\\d{1,3}){2})" +
                        "(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})" +
                        // IP address dotted notation octets
                        // excludes loopback network 0.0.0.0
                        // excludes reserved space >= 224.0.0.0
                        // excludes network & broacast addresses
                        // (first & last IP address of each class)
                        "(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])" +
                        "(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}" +
                        "(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))" +
                        "|" +
                        // host name
                        "(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)" +
                        // domain name
                        "(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*" +
                        // TLD identifier
                        "(?:\\.(?:[a-z\\u00a1-\\uffff]{2,}))" +
                        // TLD may end with dot
                        "\\.?" +
                        ")" +
                        // port number
                        "(?::\\d{2,5})?" +
                        // resource path
                        "(?:[/?#]\\S*)?" +
                        "$")) buttonSend.setEnabled(true);
                else buttonSend.setEnabled(false);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        stickyHeader = (MyStickyHeader) view.findViewById(R.id.stickyHeader);

        //create our adapters
        adapter = new FastItemAdapter<>();

        //configure our fastAdapter
        //as we provide id's for the items we want the hasStableIds enabled to speed up things
        adapter.setHasStableIds(true);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(50);

        // use a linear layout manager
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        if (savedInstanceState == null) {
            adapter.add(new RequestSettingListStickTitle(RequestSettingType.header), new
                    RequestSettingListKVItem(RequestSettingType.header), new
                    RequestSettingListStickTitle(RequestSettingType.parameter), new
                    RequestSettingListKVItem(RequestSettingType.parameter), new
                    RequestSettingListStickTitle(RequestSettingType.body), new
                    RequestSettingListBodyItem());
        } else {
            body = savedInstanceState.getString("body");
            restoreAdapter(savedInstanceState);
        }

        adapter.withEventHook(new RequestSettingListStickTitle.AddEvent()).withEventHook(new
                RequestSettingListKVItem.RemoveEvent());
        adapter.withOnClickListener(new FastAdapter.OnClickListener<IItem>() {
            @Override
            public boolean onClick(View v, IAdapter<IItem> adapter, IItem item, int position) {
                if (item instanceof RequestSettingListBodyItem) {
                    DialogFragment newFragment = new RequestRawBodyDialog();
                    newFragment.setTargetFragment(MainFragment.this, 0);
                    newFragment.show(getFragmentManager(), "dialog");
                    return true;
                }
                return false;
            }
        });

        stickyHeader.setButtonOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RequestSettingType type = stickyHeader.getType();
                if (type == RequestSettingType.body) return;
                adapter.add(RequestSettingDataUtils.lastIndexOf(adapter.getAdapterItems(), type)
                        + 1, new RequestSettingListKVItem(type));
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    InputMethodManager keyboard = (InputMethodManager) recyclerView
                            .getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    keyboard.hideSoftInputFromWindow(recyclerView.getWindowToken(), 0);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int position = ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .findFirstCompletelyVisibleItemPosition();
                if (position < 0) return;
                stickyHeader.setType(RequestSettingDataUtils.findTitleTypeBeforeIndex(adapter
                        .getAdapterItems(), position));
                if (adapter.getAdapterItems().get(position) instanceof
                        RequestSettingListStickTitle) {
                    View title = recyclerView.getLayoutManager().findViewByPosition(position);
                    float distance = title.getY() - stickyHeader.getY();
                    int stickyHeaderHeight = stickyHeader.getHeight();
                    float stickyHeaderTranslationY = stickyHeader.getTranslationY();
                    if (distance < stickyHeaderHeight || stickyHeaderTranslationY < 0)
                        stickyHeader.setTranslationY(Math.min(stickyHeaderTranslationY + distance -
                                stickyHeaderHeight, 0));
                } else stickyHeader.setTranslationY(0);
            }
        });

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RequestRecord requestRecord = new RequestRecord();
                requestRecord.setCreateAt(System.currentTimeMillis());
                requestRecord.setMethod(spinnerMethod.getSelectedItem().toString());
                requestRecord.setHttp(spinnerHttp.getSelectedItem().toString());
                requestRecord.setUrl(editTextUrl.getText().toString());
                requestRecord.setHeaders(pairListToJSONArray(getHeaders()).toString());
                requestRecord.setParameters(pairListToJSONArray(getParameters()).toString());
                requestRecord.setBody(body);
                requestRecordDao.insert(requestRecord);

                Intent intent = new Intent(getContext(), ResultActivity.class);
                intent.putExtra("method", spinnerMethod.getSelectedItem().toString());
                intent.putExtra("http", spinnerHttp.getSelectedItem().toString());
                intent.putExtra("url", editTextUrl.getText().toString());
                intent.putExtra("header", getHeaders());
                intent.putExtra("parameter", getParameters());
                intent.putExtra("body", body);
                startActivity(intent);
            }
        });

        final Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main_fragment);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menuHistory) {
                    startActivityForResult(new Intent(getContext(), RequestHistoryActivity.class),
                            HISTORY_CODE);
                    return true;
                } else if (item.getItemId() == R.id.menuClear) {
                    DialogFragment newFragment = new ClearRequestDialog();
                    newFragment.setTargetFragment(MainFragment.this, 0);
                    newFragment.show(getFragmentManager(), "dialog");
                    return true;
                }
                return false;
            }
        });
        return view;
    }

    public void clearAll() {
        spinnerMethod.setSelection(0);
        spinnerHttp.setSelection(0);
        editTextUrl.setText(null);
        adapter.set(Arrays.<IItem>asList(new RequestSettingListStickTitle
                (RequestSettingType.header), new RequestSettingListKVItem
                (RequestSettingType.header), new RequestSettingListStickTitle
                (RequestSettingType.parameter), new RequestSettingListKVItem
                (RequestSettingType.parameter), new RequestSettingListStickTitle
                (RequestSettingType.body), new RequestSettingListBodyItem()));
        body = null;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public ArrayList<MyPair> getHeaders() {
        ArrayList<MyPair> header = new ArrayList<>();
        for (IItem iItem : adapter.getAdapterItems()) {
            RequestSettingListKVItem item;
            if (iItem instanceof RequestSettingListKVItem && (item = (RequestSettingListKVItem)
                    iItem).getRequestSettingType() == RequestSettingType.header) {
                String key;
                if ((key = item.getKey()) != null && !key.isEmpty()) {
                    String value = item.getValue();
                    header.add(new MyPair(key, value == null ? "" : value));
                }
            }
        }
        return header;
    }

    public ArrayList<MyPair> getParameters() {
        ArrayList<MyPair> param = new ArrayList<>();
        for (IItem iItem : adapter.getAdapterItems()) {
            RequestSettingListKVItem item;
            if (iItem instanceof RequestSettingListKVItem && (item = (RequestSettingListKVItem)
                    iItem).getRequestSettingType() == RequestSettingType.parameter) {
                String key;
                if ((key = item.getKey()) != null && !key.isEmpty()) {
                    String value = item.getValue();
                    param.add(new MyPair(key, value == null ? "" : value));
                }
            }
        }
        return param;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("body", body);
        saveAdapter(outState);
    }

    private void saveAdapter(Bundle outState) {
        outState = adapter.saveInstanceState(outState);
        outState.putSerializable("dataSet", new ArrayList<>(adapter.getAdapterItems()));
        outState.putSerializable("stickyHeader", stickyHeader.getType());
    }

    private void restoreAdapter(Bundle savedInstanceState) {
        stickyHeader.setType((RequestSettingType) savedInstanceState.getSerializable
                ("stickyHeader"));
        adapter.set((ArrayList<IItem>) savedInstanceState.getSerializable("dataSet"));
        adapter.withSavedInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == HISTORY_CODE && resultCode == RESULT_OK) {
            RequestRecord requestRecord = requestRecordDao.queryBuilder().where(RequestRecordDao
                    .Properties.Id.eq(data.getLongExtra("requestRecordId", -1L))).build().unique();
            if (requestRecord == null) return;
            int spinnerMethodSelection = indexOfStringInArray(getResources().getStringArray(R.array
                    .methods_array), requestRecord.getMethod());
            int spinnerHttpSelection = indexOfStringInArray(getResources().getStringArray(R.array
                    .http_array), requestRecord.getHttp());
            if (spinnerHttpSelection == -1 || spinnerMethodSelection == -1) return;
            ArrayList<IItem> dataSet = new ArrayList<>();
            try {
                dataSet.add(new RequestSettingListStickTitle(RequestSettingType.header));
                String headerString = requestRecord.getHeaders();
                List<MyPair> headers;
                if (headerString == null || (headers = jsonArrayToPairList(new JSONArray
                        (headerString))).size() == 0)
                    dataSet.add(new RequestSettingListKVItem(RequestSettingType.header));
                else {
                    for (MyPair myPair : headers) {
                        dataSet.add(new RequestSettingListKVItem(RequestSettingType.header,
                                myPair.getFirst(), myPair.getSecond()));
                    }
                }
                dataSet.add(new RequestSettingListStickTitle(RequestSettingType.parameter));
                String paramString = requestRecord.getParameters();
                List<MyPair> parameters;
                if (paramString == null || (parameters = jsonArrayToPairList(new JSONArray
                        (paramString))).size() == 0)
                    dataSet.add(new RequestSettingListKVItem(RequestSettingType.parameter));
                else {
                    for (MyPair myPair : parameters) {
                        dataSet.add(new RequestSettingListKVItem(RequestSettingType.parameter,
                                myPair.getFirst(), myPair.getSecond()));
                    }
                }
                dataSet.add(new RequestSettingListStickTitle(RequestSettingType.body));
                dataSet.add(new RequestSettingListBodyItem());
            } catch (JSONException e) {
                return;
            }
            spinnerMethod.setSelection(spinnerMethodSelection);
            spinnerHttp.setSelection(spinnerHttpSelection);
            editTextUrl.setText(requestRecord.getUrl());
            adapter.set(dataSet);
            body = requestRecord.getBody();
        } else super.onActivityResult(requestCode, resultCode, data);
    }

    int indexOfStringInArray(String[] array, String s) {
        if (array == null || s == null) return -1;
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(s)) return i;
        }
        return -1;
    }
}