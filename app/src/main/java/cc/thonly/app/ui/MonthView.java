package cc.thonly.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import cc.thonly.app.R;

/**
 * 自绘月历网格。
 *
 * <p>不引第三方日历控件，高度随宽度自适应，正好 7 列。
 * 有活动的日期在数字下方画一个圆点。
 */
public class MonthView extends View {

    public interface OnDaySelectedListener {
        void onDaySelected(int year, int month, int day);
    }

    private static final String[] WEEK_LABELS = {"日", "一", "二", "三", "四", "五", "六"};

    private static final float CELL_HEIGHT_RATIO = 0.92f;
    private static final float HEADER_HEIGHT_RATIO = 0.62f;
    private static final float TEXT_SIZE_RATIO = 0.32f;
    private static final float HEADER_TEXT_SIZE_RATIO = 0.30f;
    private static final float SELECTED_RADIUS_RATIO = 0.36f;
    private static final float DOT_RADIUS_RATIO = 0.045f;
    private static final float MIN_STROKE_WIDTH_DP = 1.5f;

    private static final int COLUMN_COUNT = 7;
    private static final int WEEKEND_SUNDAY = 0;
    private static final int WEEKEND_SATURDAY = 6;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Set<Integer> markedDays = new HashSet<Integer>();

    private final int colorAccent;
    private final int colorPrimary;
    private final int colorSecondary;
    private final int colorDot;
    private final int colorSelectedText;

    private int year;
    private int month;
    private int selectedDay;
    private int daysInMonth = 30;
    private int firstWeekday;
    private int rowCount = 5;

    private float cellWidth;
    private float cellHeight;
    private float headerHeight;

    private OnDaySelectedListener listener;

    public MonthView(Context context) {
        this(context, null);
    }

    public MonthView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MonthView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        colorAccent = context.getColor(R.color.accent);
        colorPrimary = context.getColor(R.color.text_primary);
        colorSecondary = context.getColor(R.color.text_secondary);
        colorDot = context.getColor(R.color.dot);
        colorSelectedText = context.getColor(R.color.on_accent);

        textPaint.setTextAlign(Paint.Align.CENTER);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    public void setMonth(int year, int month) {
        this.year = year;
        this.month = month;

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        firstWeekday = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        rowCount = (firstWeekday + daysInMonth + COLUMN_COUNT - 1) / COLUMN_COUNT;

        requestLayout();
        invalidate();
    }

    public void setMarkedDays(Set<Integer> days) {
        markedDays.clear();
        if (days != null) {
            markedDays.addAll(days);
        }
        invalidate();
    }

    public void setSelectedDay(int day) {
        selectedDay = day;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        cellWidth = width / (float) COLUMN_COUNT;
        cellHeight = cellWidth * CELL_HEIGHT_RATIO;
        headerHeight = cellWidth * HEADER_HEIGHT_RATIO;
        setMeasuredDimension(width, Math.round(headerHeight + cellHeight * rowCount));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawWeekHeader(canvas);
        drawDays(canvas);
    }

    private void drawWeekHeader(Canvas canvas) {
        textPaint.setTextSize(cellWidth * HEADER_TEXT_SIZE_RATIO);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setColor(colorSecondary);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            float x = cellWidth * column + cellWidth / 2f;
            float y = headerHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(WEEK_LABELS[column], x, y, textPaint);
        }
    }

    private void drawDays(Canvas canvas) {
        Calendar today = Calendar.getInstance();
        boolean sameMonth = today.get(Calendar.YEAR) == year
                && today.get(Calendar.MONTH) + 1 == month;
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        float radius = Math.min(cellWidth, cellHeight) * SELECTED_RADIUS_RATIO;

        for (int index = 0; index < daysInMonth; index++) {
            int day = index + 1;
            int slot = firstWeekday + index;
            int row = slot / COLUMN_COUNT;
            int column = slot % COLUMN_COUNT;

            float centerX = cellWidth * column + cellWidth / 2f;
            float centerY = headerHeight + cellHeight * row + cellHeight / 2f;

            boolean selected = day == selectedDay;
            boolean isToday = sameMonth && day == todayDay;

            if (selected) {
                fillPaint.setColor(colorAccent);
                canvas.drawCircle(centerX, centerY, radius, fillPaint);
            } else if (isToday) {
                drawTodayRing(canvas, centerX, centerY, radius);
            }

            drawDayText(canvas, day, centerX, centerY, column, selected);

            if (markedDays.contains(day) && !selected) {
                dotPaint.setColor(colorDot);
                canvas.drawCircle(centerX, centerY + radius * 0.92f,
                        cellWidth * DOT_RADIUS_RATIO, dotPaint);
            }
        }
    }

    private void drawTodayRing(Canvas canvas, float centerX, float centerY, float radius) {
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeWidth(Math.max(MIN_STROKE_WIDTH_DP * getResources().getDisplayMetrics().density,
                cellWidth * 0.018f));
        fillPaint.setColor(colorAccent);
        canvas.drawCircle(centerX, centerY, radius, fillPaint);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    private void drawDayText(Canvas canvas, int day, float centerX, float centerY,
                             int column, boolean selected) {
        textPaint.setTextSize(cellWidth * TEXT_SIZE_RATIO);
        if (selected) {
            textPaint.setColor(colorSelectedText);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else {
            boolean weekend = column == WEEKEND_SUNDAY || column == WEEKEND_SATURDAY;
            textPaint.setColor(weekend ? colorAccent : colorPrimary);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        float baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(String.valueOf(day), centerX, baseline, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }

        float y = event.getY();
        if (y < headerHeight) {
            return true;
        }
        int column = (int) (event.getX() / cellWidth);
        int row = (int) ((y - headerHeight) / cellHeight);
        if (column < 0 || column >= COLUMN_COUNT || row < 0) {
            return true;
        }

        int day = row * COLUMN_COUNT + column - firstWeekday + 1;
        if (day < 1 || day > daysInMonth) {
            return true;
        }

        selectedDay = day;
        invalidate();
        if (listener != null) {
            listener.onDaySelected(year, month, day);
        }
        return true;
    }
}
