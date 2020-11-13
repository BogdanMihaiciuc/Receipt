package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;

public class RepeatDateAdapter extends BaseAdapter {

    final static int ContentTypeDate = 0;
    final static int ContentTypeWeekday = 1;

    static class Content {
        int type;
        String title;
    }

    final static int RowTypeHeader = 0;
    final static int RowTypeWeekdayRow = 1;
    final static int RowTypeDateRow = 2;
    final static int RowTypeDateOffset = 8;

    private ArrayList<Content> contents = new ArrayList<Content>();
    private Activity activity;

    public RepeatDateAdapter(Activity activity) {
        this.activity = activity;
    }

    public void addContent(String title, int type) {
        Content content = new Content();
        content.title = title;
        content.type = type;
        contents.add(content);
    }

    @Override
    public int getCount() {
        int count = 0;

        for (Content content : contents) {
            if (content.type == ContentTypeWeekday) {
                count += 2;
            }
            if (content.type == ContentTypeDate) {
                count += 6;
            }
        }

        return count;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return getRowType(position) == RowTypeHeader ? 0 : 1;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    protected int getRowType(int position) {

        int count = 0;

        for (Content content : contents) {
            if (content.type == ContentTypeWeekday) {
                count += 2;
                if (position == count - 2) {
                    return RowTypeHeader;
                }
                if (position == count - 1) {
                    return RowTypeWeekdayRow;
                }
            }
            if (content.type == ContentTypeDate) {
                count += 6;
                if (position == count - 6) {
                    return RowTypeHeader;
                }
                if (position >= count - 5 && position <= count - 1) {
                    return RowTypeDateRow | ((count - 5) << RowTypeDateOffset);
                }
            }
        }

        throw new IllegalArgumentException("The position does not correspond to any row type.");

    }

    protected Content getContent(int position) {

        int count = 0;
        int index = 0;

        for (Content content : contents) {
            if (content.type == ContentTypeWeekday) {
                count += 2;
                if (position >= count - 2 && position <= count - 1) {
                    return contents.get(index);
                }
            }
            if (content.type == ContentTypeDate) {
                count += 6;
                if (position >= count - 6 && position <= count - 1) {
                    return contents.get(index);
                }
            }
            index++;
        }

        throw new IllegalArgumentException("The position does not correspond to any row type.");

    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {

        Content content = getContent(position);
        int rowType = getRowType(position);
        int dateOffset = 0;
        if (rowType == RowTypeDateRow) {
            dateOffset = rowType >> RowTypeDateOffset;
        }

        if (convertView == null) {
            if (rowType == RowTypeHeader) {
                convertView = activity.getLayoutInflater().inflate(R.layout.history_header, container, false);
            }
            else {
                convertView = activity.getLayoutInflater().inflate(R.layout.welcome_date_row, container, false);
            }
        }

        if (rowType == RowTypeHeader) {
            ((TextView) convertView.findViewById(R.id.HeaderTitle)).setText(content.title);
        }
        else {
            final ViewGroup ConvertView = (ViewGroup) convertView;
            Calendar calendar = Calendar.getInstance();
            if (rowType == RowTypeWeekdayRow) {

                for (int i = 0; i < 7; i++) {
                    calendar.set(Calendar.DAY_OF_WEEK, i + 1);
                    String displayName =  calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, activity.getResources().getConfiguration().locale);

                    ((TextView) ConvertView.getChildAt(i)).setText("" + displayName.charAt(0) + displayName.charAt(1));
                    ConvertView.getChildAt(i).setVisibility(View.VISIBLE);
                }
            }
            else {
                for (int i = 0; i < 7; i++) {
                    ((TextView) ConvertView.getChildAt(i)).setText("" + (i + 1 + dateOffset));
                    if (i + dateOffset < 31) {
                        ConvertView.getChildAt(i).setVisibility(View.VISIBLE);
                    }
                    else {
                        ConvertView.getChildAt(i).setVisibility(View.INVISIBLE);
                    }
                }
            }
        }

        return convertView;
    }
}
