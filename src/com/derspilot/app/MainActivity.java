package com.derspilot.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "derspilot_prefs";
    private static final String KEY_COURSES = "courses";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_QUIZZES = "quizzes";
    private static final String KEY_RESULTS = "quiz_results";
    private static final String KEY_NAME = "name";
    private static final String KEY_CLASS = "class_name";
    private static final String KEY_DAILY_GOAL = "daily_goal";
    private static final String KEY_IS_PRO = "is_pro";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_WEEK_START = "week_start";
    private static final String KEY_POMODORO_WORK = "pomodoro_work";
    private static final String KEY_POMODORO_BREAK = "pomodoro_break";
    private static final String KEY_AUTO_REVIEW = "auto_review";
    private static final String KEY_EXAM_WARNING = "exam_warning";
    private static final String KEY_NOTIFY_DAILY = "notify_daily";
    private static final String KEY_NOTIFY_EXAM = "notify_exam";
    private static final String KEY_NOTIFY_PLAN = "notify_plan";
    private static final String KEY_NOTIFY_POMODORO = "notify_pomodoro";
    private static final String KEY_NOTIFY_MOTIVATION = "notify_motivation";
    private static final String KEY_SHOW_AD_TEST = "show_ad_test";

    private static final int TAB_HOME = 0;
    private static final int TAB_COURSES = 1;
    private static final int TAB_PLAN = 2;
    private static final int TAB_QUIZ = 3;
    private static final int TAB_PROFILE = 4;
    private static final int TAB_POMODORO = 5;

    private static final String STATUS_NOT_STARTED = "Başlanmadı";
    private static final String STATUS_IN_PROGRESS = "Devam ediyor";
    private static final String STATUS_DONE = "Tamamlandı";
    private static final String TASK_PLANNED = "Planlandı";
    private static final String TASK_DONE = "Tamamlandı";
    private static final String TASK_DEFERRED = "Ertelendi";
    private static final String CHANNEL_ID = "derspilot_focus";

    private final Locale tr = new Locale("tr", "TR");
    private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat longDate = new SimpleDateFormat("d MMMM yyyy, EEEE", tr);
    private final SimpleDateFormat shortDate = new SimpleDateFormat("d MMM", tr);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private Palette colors;
    private LinearLayout root;
    private LinearLayout body;
    private LinearLayout bottomNav;
    private int selectedTab = TAB_HOME;
    private int planMode = 0;
    private String courseDetailId = "";

    private final ArrayList<Course> courses = new ArrayList<>();
    private final ArrayList<StudyTask> tasks = new ArrayList<>();
    private final ArrayList<PomodoroSession> sessions = new ArrayList<>();
    private final ArrayList<QuizQuestion> quizzes = new ArrayList<>();
    private final ArrayList<QuizResult> results = new ArrayList<>();

    private TextView timerText;
    private TextView timerCourseText;
    private boolean timerRunning = false;
    private int selectedPomodoroMinutes = 25;
    private int remainingSeconds = 25 * 60;
    private String activeCourseId = "";
    private String activeTopicId = "";

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) {
                return;
            }
            remainingSeconds--;
            updateTimerLabel();
            if (remainingSeconds <= 0) {
                timerRunning = false;
                completePomodoro(true);
                return;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        seedPrefs();
        loadData();
        selectedPomodoroMinutes = prefs.getInt(KEY_POMODORO_WORK, 25);
        remainingSeconds = selectedPomodoroMinutes * 60;
        createNotificationChannel();
        requestNotificationPermission();
        setContentView(buildShell());
        render();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(18), dp(16), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void render() {
        colors = palette();
        configureWindow();
        root.setBackgroundColor(colors.bg);
        bottomNav.setBackgroundColor(colors.surface);
        body.removeAllViews();
        body.setBackgroundColor(colors.bg);

        if (selectedTab == TAB_HOME) {
            renderHome();
        } else if (selectedTab == TAB_COURSES) {
            renderCourses();
        } else if (selectedTab == TAB_PLAN) {
            renderPlan();
        } else if (selectedTab == TAB_QUIZ) {
            renderQuiz();
        } else if (selectedTab == TAB_PROFILE) {
            renderProfile();
        } else {
            renderPomodoro();
        }
        renderBottomNav();
    }

    private void renderBottomNav() {
        bottomNav.removeAllViews();
        bottomNav.addView(navButton("Ana Sayfa", TAB_HOME), navParams());
        bottomNav.addView(navButton("Hedefler", TAB_COURSES), navParams());
        bottomNav.addView(navButton("Plan", TAB_PLAN), navParams());
        bottomNav.addView(navButton("Quiz", TAB_QUIZ), navParams());
        bottomNav.addView(navButton("Profil", TAB_PROFILE), navParams());
    }

    private Button navButton(String label, final int tab) {
        boolean active = selectedTab == tab;
        Button button = smallButton(label, active ? colors.primary : colors.surfaceAlt, active ? Color.WHITE : colors.text);
        button.setTextSize(11);
        button.setPadding(dp(4), dp(9), dp(4), dp(9));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTab = tab;
                if (tab != TAB_COURSES) {
                    courseDetailId = "";
                }
                render();
            }
        });
        return button;
    }

    private void renderHome() {
        int todayCount = todayTasks(false, false).size();
        buildTopHeader(
                "Merhaba, bugün " + todayCount + " çalışma görevin var",
                longDate.format(new Date()),
                motivationText());

        if (courses.isEmpty()) {
            body.addView(emptyCard(
                    "Hoş geldin",
                    "Önce günlük hedefini ve ilk çalışma alanını ekle. Bu bir ders, kurs, dil hedefi, sertifika ya da kişisel öğrenme planı olabilir.",
                    "İlk Çalışmanı Ekle",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showCourseDialog(null);
                        }
                    }), blockParams());
        }

        LinearLayout focus = card();
        focus.addView(text("Bugünkü Odak", 20, colors.text, Typeface.BOLD));
        TextView summary = text(todayCount + " açık görev · " + formatMinutes(todayPlannedMinutes()) + " planlandı · günlük hedef " + formatMinutes(dailyGoal()), 14, colors.muted, Typeface.NORMAL);
        summary.setPadding(0, dp(4), 0, dp(12));
        focus.addView(summary);
        LinearLayout focusActions = row();
        focusActions.addView(actionButton("Plan Oluştur", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateDailyPlan();
            }
        }), weightParams(true));
        focusActions.addView(actionButton("Pomodoro", colors.success, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTab = TAB_POMODORO;
                render();
            }
        }), weightParams(false));
        focus.addView(focusActions);
        body.addView(focus, blockParams());

        LinearLayout overview = row();
        overview.addView(metricCard("Bugün", formatMinutes(todayDoneMinutes()), colors.primary), weightParams(true));
        overview.addView(metricCard("İlerleme", overallProgress() + "%", colors.success), weightParams(false));
        body.addView(overview, tightBlockParams());

        body.addView(sectionTitle("Bugünün Görevleri"), sectionParams());
        ArrayList<StudyTask> today = todayTasks(true);
        if (today.isEmpty()) {
            body.addView(emptyCard(
                    "Bugün için plan yok",
                    "Çalışma alanlarını ve başlıklarını ekledikten sonra günlük planı otomatik oluşturabilirsin.",
                    "Bugünün Planını Oluştur",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            generateDailyPlan();
                        }
                    }), blockParams());
        } else {
            for (StudyTask task : today) {
                body.addView(taskCard(task, true), cardGapParams());
            }
        }

    }

    private void renderCourses() {
        if (!TextUtils.isEmpty(courseDetailId)) {
            Course selected = findCourse(courseDetailId);
            if (selected != null) {
                renderCourseDetail(selected);
                return;
            }
            courseDetailId = "";
        }

        buildScreenHeader("Çalışmalar", "Ders, kurs, dil, sertifika veya kişisel hedeflerini sade biçimde takip et.", "Ekle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCourseDialog(null);
            }
        });

        if (!isPro()) {
            body.addView(limitCard("Ücretsiz sürüm: 3 çalışma alanı ve alan başına 10 başlık. Pro bağlantı noktası hazır; Play Billing kimlikleri eklenince gerçek satın alma açılacak."), blockParams());
        }

        if (courses.isEmpty()) {
            body.addView(emptyCard(
                    "İlk çalışma alanını ekle",
                    "Bir ders, kurs, dil hedefi ya da sınav hazırlığı eklediğinde DersPilot plan üretmeye başlar.",
                    "Ekle",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showCourseDialog(null);
                        }
                    }), blockParams());
        } else {
            for (Course course : courses) {
                body.addView(courseCard(course), cardGapParams());
            }
        }
        addAdPlacement(body, "Hedefler alt banner");
    }

    private void renderCourseDetail(final Course course) {
        LinearLayout header = card();
        Button back = subtleButton("Geri", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                courseDetailId = "";
                render();
            }
        });
        header.addView(back, smallBlockParams());
        header.addView(text(course.name, 25, colors.text, Typeface.BOLD));
        header.addView(text("Hedef tarihi: " + safe(course.examDate, "Tarih yok") + " · " + course.examType + " · hedef " + course.targetGrade, 14, colors.muted, Typeface.NORMAL));
        header.addView(progressBar(courseProgress(course), course.color), progressParams());
        body.addView(header, blockParams());

        LinearLayout actions = row();
        actions.addView(actionButton("Başlık Ekle", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTopicDialog(course, null);
            }
        }), weightParams(true));
        actions.addView(actionButton("Hedef Planı", colors.success, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateExamPlanForCourse(course);
            }
        }), weightParams(false));
        body.addView(actions, blockParams());

        body.addView(sectionTitle("Başlıklar"), sectionParams());
        if (course.topics.isEmpty()) {
            body.addView(emptyCard("Başlık eklenmedi", "Çalışılacak başlıkları zorluk ve öncelikle eklersen otomatik plan daha iyi çalışır.", "Başlık Ekle", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTopicDialog(course, null);
                }
            }), blockParams());
        } else {
            for (final Topic topic : course.topics) {
                body.addView(topicCard(course, topic), cardGapParams());
            }
        }
    }

    private void renderPlan() {
        buildScreenHeader("Plan", "Günlük, haftalık ve hedefe özel çalışma takvimini oluştur.", "Otomatik Plan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateWeeklyPlan();
            }
        });

        LinearLayout tabs = row();
        tabs.addView(segmentButton("Günlük", planMode == 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                planMode = 0;
                render();
            }
        }), weightParams(true));
        tabs.addView(segmentButton("Haftalık", planMode == 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                planMode = 1;
                render();
            }
        }), weightParams(true));
        tabs.addView(segmentButton("Hedef", planMode == 2, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                planMode = 2;
                render();
            }
        }), weightParams(false));
        body.addView(tabs, blockParams());

        if (planMode == 0) {
            renderDailyPlan();
        } else if (planMode == 1) {
            renderWeeklyPlan();
        } else {
            renderExamPlan();
        }
        addAdPlacement(body, "Plan alt banner");
    }

    private void renderDailyPlan() {
        LinearLayout controls = row();
        controls.addView(actionButton("Bugünü Oluştur", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateDailyPlan();
            }
        }), weightParams(true));
        controls.addView(actionButton("Ertelenenleri Topla", colors.warn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pullDeferredToToday();
            }
        }), weightParams(false));
        body.addView(controls, tightBlockParams());

        ArrayList<StudyTask> today = todayTasks(true);
        if (today.isEmpty()) {
            body.addView(infoCard("Bugün için plan yok. DersPilot eksik ve zor başlıkları öne alarak plan oluşturabilir."), blockParams());
        } else {
            for (StudyTask task : today) {
                body.addView(taskCard(task, true), cardGapParams());
            }
        }
    }

    private void renderWeeklyPlan() {
        LinearLayout controls = row();
        controls.addView(actionButton("7 Günlük Plan", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateWeeklyPlan();
            }
        }), weightParams(true));
        controls.addView(actionButton("Tüm Planı Temizle", colors.danger, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearFutureTasks();
            }
        }), weightParams(false));
        body.addView(controls, tightBlockParams());

        boolean hasAny = false;
        for (int day = 0; day < 7; day++) {
            String date = addDays(today(), day);
            ArrayList<StudyTask> dayTasks = tasksForDate(date);
            if (!dayTasks.isEmpty()) {
                hasAny = true;
                body.addView(sectionTitle(day == 0 ? "Bugün" : shortDateLabel(date)), sectionParams());
                for (StudyTask task : dayTasks) {
                    body.addView(taskCard(task, false), cardGapParams());
                }
            }
        }
        if (!hasAny) {
            body.addView(infoCard("Haftalık plan boş. 7 günlük plan oluşturduğunda zor ve yüksek öncelikli başlıklar öne alınır."), blockParams());
        }
    }

    private void renderExamPlan() {
        if (courses.isEmpty()) {
            body.addView(infoCard("Hedefe özel plan için önce çalışma alanı ve hedef tarihi ekle."), blockParams());
            return;
        }
        for (final Course course : upcomingCourses()) {
            LinearLayout card = card();
            card.addView(text(course.name, 19, colors.text, Typeface.BOLD));
            card.addView(text("Hedefe " + daysUntilText(course.examDate) + " · eksik başlık: " + incompleteTopics(course).size(), 14, colors.muted, Typeface.NORMAL));
            card.addView(progressBar(courseProgress(course), course.color), progressParams());
            card.addView(actionButton("Bu Hedefe Plan Oluştur", colors.primary, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    generateExamPlanForCourse(course);
                }
            }), smallBlockParams());
            body.addView(card, cardGapParams());
        }
    }

    private void renderPomodoro() {
        buildScreenHeader("Pomodoro", "Çalışma alanı seç, odağı başlat, çalışmanın sonunda başlığı tamamlandı işaretle.", "Ana Sayfa", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTab = TAB_HOME;
                render();
            }
        });

        LinearLayout timerCard = card();
        timerCourseText = text(activeStudyLabel(), 15, colors.muted, Typeface.BOLD);
        timerCard.addView(timerCourseText);
        timerText = text(formatTimer(remainingSeconds), 54, colors.text, Typeface.BOLD);
        timerText.setGravity(Gravity.CENTER);
        timerText.setPadding(0, dp(18), 0, dp(12));
        timerCard.addView(timerText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout modesOne = row();
        modesOne.addView(segmentButton("25 / 5", selectedPomodoroMinutes == 25, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPomodoroMode(25);
            }
        }), weightParams(true));
        modesOne.addView(segmentButton("50 / 10", selectedPomodoroMinutes == 50, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPomodoroMode(50);
            }
        }), weightParams(false));
        timerCard.addView(modesOne, smallBlockParams());

        LinearLayout modesTwo = row();
        modesTwo.addView(segmentButton("Özel 15", selectedPomodoroMinutes == 15, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPomodoroMode(15);
            }
        }), weightParams(true));
        modesTwo.addView(segmentButton("Derin Odak 90", selectedPomodoroMinutes == 90, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPomodoroMode(90);
            }
        }), weightParams(false));
        timerCard.addView(modesTwo, smallBlockParams());

        LinearLayout picker = row();
        picker.addView(actionButton("Alan Seç", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCourse(new CourseCallback() {
                    @Override
                    public void onCourse(Course course) {
                        activeCourseId = course.id;
                        if (!course.topics.isEmpty()) {
                            activeTopicId = course.topics.get(0).id;
                        }
                        render();
                    }
                });
            }
        }), weightParams(true));
        picker.addView(actionButton("Başlık Seç", colors.success, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Course course = findCourse(activeCourseId);
                if (course == null) {
                    chooseCourse(new CourseCallback() {
                        @Override
                        public void onCourse(final Course selected) {
                            activeCourseId = selected.id;
                            chooseTopic(selected, new TopicCallback() {
                                @Override
                                public void onTopic(Topic topic) {
                                    activeTopicId = topic.id;
                                    render();
                                }
                            });
                        }
                    });
                    return;
                }
                chooseTopic(course, new TopicCallback() {
                    @Override
                    public void onTopic(Topic topic) {
                        activeTopicId = topic.id;
                        render();
                    }
                });
            }
        }), weightParams(false));
        timerCard.addView(picker, smallBlockParams());

        LinearLayout controls = row();
        controls.addView(actionButton(timerRunning ? "Duraklat" : "Başlat", colors.warn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTimer();
            }
        }), weightParams(true));
        controls.addView(actionButton("Bitir", colors.danger, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completePomodoro(false);
            }
        }), weightParams(false));
        timerCard.addView(controls, smallBlockParams());
        body.addView(timerCard, blockParams());

        body.addView(infoCard("Pro alanı: sınırsız Pomodoro geçmişi, haftalık odak raporu ve odak puanı için veri modeli hazır."), blockParams());
    }

    private void renderQuiz() {
        buildScreenHeader("Quiz", "Manuel sorularla tekrar yap ve zayıf başlıkları plana geri ekle.", "Quiz Oluştur", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQuizCreateFlow();
            }
        });

        if (quizzes.isEmpty()) {
            body.addView(emptyCard(
                    "İlk quiz sorunu oluştur",
                    "Çalışma alanı ve başlık seçip doğru/yanlış ya da çoktan seçmeli bir soru ekleyebilirsin.",
                    "Quiz Oluştur",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showQuizCreateFlow();
                        }
                    }), blockParams());
        } else {
            body.addView(quizStatsCard(), blockParams());
            for (final QuizQuestion quiz : quizzes) {
                body.addView(quizQuestionCard(quiz), cardGapParams());
            }
        }
        addAdPlacement(body, "Quiz sonuç alt banner");
    }

    private void renderProfile() {
        buildScreenHeader("Profil", "Hedef, istatistik, tema ve uygulama ayarları.", "Profili Düzenle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProfileDialog();
            }
        });

        LinearLayout statsOne = row();
        statsOne.addView(metricCard("Toplam çalışma", formatMinutes(totalStudyMinutes()), colors.primary), weightParams(true));
        statsOne.addView(metricCard("Tamamlanan başlık", String.valueOf(completedTopicCount()), colors.success), weightParams(false));
        body.addView(statsOne, tightBlockParams());

        LinearLayout statsTwo = row();
        statsTwo.addView(metricCard("Seri gün", String.valueOf(streakDays()), colors.warn), weightParams(true));
        statsTwo.addView(metricCard("Pro durumu", isPro() ? "Aktif" : "Ücretsiz", colors.pro), weightParams(false));
        body.addView(statsTwo, tightBlockParams());

        body.addView(profileInfoCard(), blockParams());
        body.addView(settingsCard(), blockParams());
        body.addView(proCard(), blockParams());
    }

    private void buildTopHeader(String title, String date, String subtitle) {
        LinearLayout header = card();
        header.addView(text("DersPilot", 15, colors.primary, Typeface.BOLD));
        TextView titleView = text(title, 25, colors.text, Typeface.BOLD);
        titleView.setPadding(0, dp(6), 0, dp(4));
        header.addView(titleView);
        header.addView(text(date, 14, colors.muted, Typeface.NORMAL));
        TextView sub = text(subtitle, 14, colors.text, Typeface.NORMAL);
        sub.setPadding(0, dp(12), 0, 0);
        header.addView(sub);
        body.addView(header, blockParams());
    }

    private void buildScreenHeader(String title, String subtitle, String action, View.OnClickListener listener) {
        LinearLayout header = card();
        LinearLayout row = row();
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.addView(text(title, 26, colors.text, Typeface.BOLD));
        TextView sub = text(subtitle, 14, colors.muted, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        titleBlock.addView(sub);
        row.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(smallButton(action, colors.primary, Color.WHITE), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.getChildAt(1).setOnClickListener(listener);
        header.addView(row);
        body.addView(header, blockParams());
    }

    private LinearLayout courseCard(final Course course) {
        LinearLayout card = card();
        LinearLayout top = row();
        TextView title = text(course.name, 20, colors.text, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView priority = pill(course.difficulty, difficultyColor(course.difficulty));
        top.addView(priority);
        card.addView(top);

        card.addView(text("Hedef tarihi: " + safe(course.examDate, "Tarih yok") + " · " + daysUntilText(course.examDate), 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(text("Başlık: " + course.topics.size() + " · Tamamlanan: " + completedTopics(course) + " · Hedef: " + course.targetGrade, 14, colors.muted, Typeface.NORMAL));
        card.addView(progressBar(courseProgress(course), course.color), progressParams());
        card.addView(text("İlerleme: %" + courseProgress(course) + " · Haftalık hedef: " + course.weeklyGoalHour + " saat", 13, colors.muted, Typeface.NORMAL));

        LinearLayout actionsOne = row();
        actionsOne.addView(tinyButton("Başlıklar", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                courseDetailId = course.id;
                render();
            }
        }), weightParams(true));
        actionsOne.addView(tinyButton("Planla", colors.success, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateExamPlanForCourse(course);
            }
        }), weightParams(false));
        card.addView(actionsOne, smallBlockParams());

        LinearLayout actionsTwo = row();
        actionsTwo.addView(tinyButton("Quiz", colors.pro, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQuizDialog(course, null);
            }
        }), weightParams(true));
        actionsTwo.addView(tinyButton("Düzenle", colors.surfaceAlt, colors.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCourseDialog(course);
            }
        }), weightParams(false));
        card.addView(actionsTwo, smallBlockParams());
        return card;
    }

    private LinearLayout topicCard(final Course course, final Topic topic) {
        LinearLayout card = card();
        LinearLayout top = row();
        top.addView(text(topic.title, 18, colors.text, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(pill(topic.status, statusColor(topic.status)));
        card.addView(top);
        card.addView(text("Zorluk: " + topic.difficulty + " · Öncelik: " + topic.priority + " · Süre: " + topic.estimatedMinutes + " dk", 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        if (!TextUtils.isEmpty(topic.subtitles)) {
            card.addView(text(topic.subtitles, 13, colors.muted, Typeface.NORMAL));
        }
        if (!TextUtils.isEmpty(topic.notes)) {
            card.addView(text("Not: " + topic.notes, 13, colors.muted, Typeface.NORMAL), smallBlockParams());
        }
        LinearLayout actions = row();
        actions.addView(tinyButton(topic.status.equals(STATUS_DONE) ? "Geri Al" : "Tamamlandı", colors.success, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                topic.status = topic.status.equals(STATUS_DONE) ? STATUS_IN_PROGRESS : STATUS_DONE;
                topic.completedAt = topic.status.equals(STATUS_DONE) ? nowIsoDate() : "";
                saveCourses();
                render();
            }
        }), weightParams(true));
        actions.addView(tinyButton("Planla", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSingleTask(course, topic, today());
                Toast.makeText(MainActivity.this, "Başlık bugünün planına eklendi.", Toast.LENGTH_SHORT).show();
                render();
            }
        }), weightParams(true));
        actions.addView(tinyButton("Düzenle", colors.surfaceAlt, colors.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTopicDialog(course, topic);
            }
        }), weightParams(false));
        card.addView(actions, smallBlockParams());
        return card;
    }

    private LinearLayout taskCard(final StudyTask task, boolean withActions) {
        Course course = findCourse(task.courseId);
        Topic topic = findTopic(task.courseId, task.topicId);
        int accent = course != null ? course.color : colors.primary;

        LinearLayout card = card();
        LinearLayout top = row();
        top.addView(text(task.startTime + " · " + task.duration + " dk", 15, accent, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(pill(task.status, statusColor(task.status)));
        card.addView(top);
        card.addView(text(course != null ? course.name : "Çalışma", 18, colors.text, Typeface.BOLD), smallBlockParams());
        card.addView(text(!TextUtils.isEmpty(task.title) ? task.title : (topic != null ? topic.title : "Çalışma görevi"), 14, colors.muted, Typeface.NORMAL));
        card.addView(progressBar(task.status.equals(TASK_DONE) ? 100 : 0, accent), progressParams());

        if (withActions) {
            LinearLayout actions = row();
            actions.addView(tinyButton("Başla", colors.primary, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startTask(task);
                }
            }), weightParams(true));
            actions.addView(tinyButton("Ertele", colors.warn, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deferTask(task);
                }
            }), weightParams(true));
            actions.addView(tinyButton("Tamamlandı", colors.success, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    completeTask(task);
                }
            }), weightParams(false));
            card.addView(actions, smallBlockParams());
        }
        return card;
    }

    private LinearLayout examMiniCard(Course course) {
        LinearLayout card = card();
        card.addView(text(course.name, 18, colors.text, Typeface.BOLD));
        card.addView(text(course.examType + " · " + safe(course.examDate, "Tarih yok") + " · " + daysUntilText(course.examDate), 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(progressBar(courseProgress(course), course.color), progressParams());
        return card;
    }

    private LinearLayout quizStatsCard() {
        int solved = results.size();
        int avg = 0;
        if (solved > 0) {
            int sum = 0;
            for (QuizResult result : results) {
                sum += result.score;
            }
            avg = sum / solved;
        }
        LinearLayout row = row();
        row.addView(metricCard("Çözülen quiz", String.valueOf(solved), colors.primary), weightParams(true));
        row.addView(metricCard("Ortalama başarı", avg + "%", avg >= 70 ? colors.success : colors.warn), weightParams(false));
        return row;
    }

    private LinearLayout quizQuestionCard(final QuizQuestion quiz) {
        final Course course = findCourse(quiz.courseId);
        final Topic topic = findTopic(quiz.courseId, quiz.topicId);
        LinearLayout card = card();
        card.addView(text(course != null ? course.name : "Çalışma", 14, colors.primary, Typeface.BOLD));
        card.addView(text(topic != null ? topic.title : "Genel tekrar", 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(text(quiz.question, 18, colors.text, Typeface.BOLD), smallBlockParams());
        card.addView(answerButton("A", quiz.optionA, quiz, course, topic), smallBlockParams());
        card.addView(answerButton("B", quiz.optionB, quiz, course, topic), smallBlockParams());
        card.addView(answerButton("C", quiz.optionC, quiz, course, topic), smallBlockParams());
        card.addView(answerButton("D", quiz.optionD, quiz, course, topic), smallBlockParams());
        return card;
    }

    private Button answerButton(final String key, String value, final QuizQuestion quiz, final Course course, final Topic topic) {
        Button button = smallButton(key + ") " + value, colors.surfaceAlt, colors.text);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                solveQuiz(quiz, key, course, topic);
            }
        });
        return button;
    }

    private LinearLayout profileInfoCard() {
        LinearLayout card = card();
        card.addView(text(prefs.getString(KEY_NAME, "Öğrenci"), 22, colors.text, Typeface.BOLD));
        card.addView(text(prefs.getString(KEY_CLASS, "Genel öğrenme") + " · günlük hedef " + formatMinutes(dailyGoal()), 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(text("Veriler bu sürümde cihaz içinde saklanır. Uygulama ders, kurs, dil, sertifika ve kişisel çalışma hedefleri için kullanılabilir.", 13, colors.muted, Typeface.NORMAL), smallBlockParams());
        return card;
    }

    private LinearLayout settingsCard() {
        LinearLayout card = card();
        card.addView(text("Ayarlar", 21, colors.text, Typeface.BOLD));
        card.addView(text("Tema", 14, colors.muted, Typeface.BOLD), smallBlockParams());
        LinearLayout themeRow = row();
        themeRow.addView(segmentButton("Açık", "light".equals(themeMode()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemeMode("light");
            }
        }), weightParams(true));
        themeRow.addView(segmentButton("Koyu", "dark".equals(themeMode()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemeMode("dark");
            }
        }), weightParams(true));
        themeRow.addView(segmentButton("Sistem", "system".equals(themeMode()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemeMode("system");
            }
        }), weightParams(false));
        card.addView(themeRow, smallBlockParams());

        card.addView(settingLine("Varsayılan Pomodoro", prefs.getInt(KEY_POMODORO_WORK, 25) + " dk çalışma / " + prefs.getInt(KEY_POMODORO_BREAK, 5) + " dk mola"));
        card.addView(switchLine("Çalışma hatırlatıcısı", KEY_NOTIFY_DAILY));
        card.addView(switchLine("Pomodoro bildirimi", KEY_NOTIFY_POMODORO));
        card.addView(switchLine("Reklam yerleşim testi", KEY_SHOW_AD_TEST));

        LinearLayout actions = row();
        actions.addView(actionButton("Hedefi Düzenle", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGoalDialog();
            }
        }), weightParams(true));
        actions.addView(actionButton("Verileri Dışa Aktar", colors.surfaceAlt, colors.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportDialog();
            }
        }), weightParams(false));
        card.addView(actions, smallBlockParams());
        return card;
    }

    private LinearLayout proCard() {
        LinearLayout card = card();
        LinearLayout row = row();
        row.addView(text("Pro Sürüm", 21, colors.text, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(pill(isPro() ? "Aktif" : "Hazır", colors.pro));
        card.addView(row);
        card.addView(text("Reklamsız kullanım, sınırsız çalışma alanı, sınırsız başlık, gelişmiş plan, haftalık rapor ve sınırsız quiz için ürün paketleri:", 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(settingLine("Pro Aylık", "39,99 TL"));
        card.addView(settingLine("Pro Yıllık", "249,99 TL"));
        card.addView(settingLine("Pro Ömür Boyu", "499,99 TL"));
        Button button = actionButton(isPro() ? "Pro Aktif" : "Satın Alma SDK Bağlantı Noktası", colors.pro, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Play Billing ürün kimlikleri eklenince bu buton gerçek satın alma akışını açacak.", Toast.LENGTH_LONG).show();
            }
        });
        card.addView(button, smallBlockParams());
        return card;
    }

    private LinearLayout achievementsCard() {
        LinearLayout card = card();
        card.addView(text("Başarı Rozetleri", 21, colors.text, Typeface.BOLD));
        card.addView(badgeLine("İlk Dersini Ekledin", courses.size() > 0));
        card.addView(badgeLine("3 Gün Üst Üste Çalıştın", streakDays() >= 3));
        card.addView(badgeLine("10 Konu Tamamladın", completedTopicCount() >= 10));
        card.addView(badgeLine("İlk Quizini Çözdün", results.size() > 0));
        card.addView(badgeLine("Haftalık Hedefi Tamamladın", totalStudyMinutesLastDays(7) >= weeklyGoalMinutes()));
        card.addView(badgeLine("Sınava Hazır", overallProgress() >= 85));
        return card;
    }

    private LinearLayout privacyCard() {
        LinearLayout card = card();
        card.addView(text("Gizlilik ve Yayın Notları", 21, colors.text, Typeface.BOLD));
        card.addView(text("Bu sürümde reklam SDK’sı, yapay zeka, PDF yükleme veya bulut yedekleme yoktur. Ders, konu, plan, Pomodoro ve quiz verileri cihaz içinde saklanır.", 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        card.addView(text("Google Play’e çıkmadan önce AdMob ve Play Billing için kendi uygulama kimliklerinle resmi SDK entegrasyonu yapılmalı; bu APK’da yalnızca yerleşim ve ürün akışı taslağı hazırdır.", 14, colors.muted, Typeface.NORMAL), smallBlockParams());
        return card;
    }

    private LinearLayout metricCard(String label, String value, int accent) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(text(value, 22, accent, Typeface.BOLD));
        TextView labelView = text(label, 12, colors.muted, Typeface.BOLD);
        labelView.setPadding(0, dp(3), 0, 0);
        card.addView(labelView);
        return card;
    }

    private LinearLayout infoCard(String message) {
        LinearLayout card = card();
        card.addView(text(message, 14, colors.muted, Typeface.NORMAL));
        return card;
    }

    private LinearLayout limitCard(String message) {
        LinearLayout card = card();
        card.setBackground(round(colors.softWarn, 8, colors.warn, 1));
        card.addView(text(message, 13, colors.text, Typeface.NORMAL));
        return card;
    }

    private LinearLayout emptyCard(String title, String message, String action, View.OnClickListener listener) {
        LinearLayout card = card();
        card.addView(text(title, 20, colors.text, Typeface.BOLD));
        TextView msg = text(message, 14, colors.muted, Typeface.NORMAL);
        msg.setPadding(0, dp(6), 0, dp(10));
        card.addView(msg);
        card.addView(actionButton(action, colors.primary, listener), smallBlockParams());
        return card;
    }

    private void showCourseDialog(final Course existing) {
        if (existing == null && !isPro() && courses.size() >= 3) {
            showProLimitDialog("Ücretsiz sürümde en fazla 3 çalışma alanı eklenebilir.");
            return;
        }

        final EditText name = input("Ders, kurs veya hedef adı", existing != null ? existing.name : "");
        final Spinner color = spinner(new String[]{"Mavi", "Yeşil", "Turuncu", "Mor", "Kırmızı"}, existing != null ? colorLabel(existing.color) : "Mavi");
        final EditText teacher = input("Eğitmen / not (isteğe bağlı)", existing != null ? existing.teacher : "");
        final EditText examDate = input("Hedef / sınav tarihi (yyyy-MM-dd)", existing != null ? existing.examDate : addDays(today(), 14));
        final Spinner examType = spinner(new String[]{"Vize", "Final", "Quiz", "Ödev", "Sertifika", "Kurs", "Dil", "Genel"}, existing != null ? existing.examType : "Genel");
        final Spinner difficulty = spinner(new String[]{"Kolay", "Orta", "Zor"}, existing != null ? existing.difficulty : "Orta");
        final EditText weekly = input("Haftalık hedef saat", existing != null ? String.valueOf(existing.weeklyGoalHour) : "5");
        weekly.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText targetGrade = input("Not hedefi", existing != null ? String.valueOf(existing.targetGrade) : "80");
        targetGrade.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText description = input("Açıklama / hedef notu", existing != null ? existing.description : "");

        LinearLayout form = dialogForm();
        form.addView(name);
        form.addView(label("Renk"));
        form.addView(color);
        form.addView(teacher);
        form.addView(examDate);
        form.addView(label("Hedef türü"));
        form.addView(examType);
        form.addView(label("Zorluk seviyesi"));
        form.addView(difficulty);
        form.addView(weekly);
        form.addView(targetGrade);
        form.addView(description);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Çalışma Alanı Ekle" : "Çalışma Alanını Düzenle")
                .setView(scrollWrap(form))
                .setPositiveButton(existing == null ? "Ekle" : "Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = name.getText().toString().trim();
                        if (TextUtils.isEmpty(title)) {
                            Toast.makeText(MainActivity.this, "Ad gerekli.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Course course = existing != null ? existing : new Course();
                        course.id = existing != null ? existing.id : nextId("course");
                        course.name = title;
                        course.color = colorForLabel(color.getSelectedItem().toString());
                        course.teacher = teacher.getText().toString().trim();
                        course.examDate = examDate.getText().toString().trim();
                        course.examType = examType.getSelectedItem().toString();
                        course.difficulty = difficulty.getSelectedItem().toString();
                        course.weeklyGoalHour = parseInt(weekly.getText().toString(), 5);
                        course.targetGrade = parseInt(targetGrade.getText().toString(), 80);
                        course.description = description.getText().toString().trim();
                        course.createdAt = TextUtils.isEmpty(course.createdAt) ? nowMillis() : course.createdAt;
                        if (existing == null) {
                            courses.add(course);
                        }
                        saveCourses();
                        courseDetailId = course.id;
                        selectedTab = TAB_COURSES;
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showTopicDialog(final Course course, final Topic existing) {
        if (existing == null && !isPro() && course.topics.size() >= 10) {
            showProLimitDialog("Ücretsiz sürümde her çalışma alanı için en fazla 10 başlık eklenebilir.");
            return;
        }

        final EditText title = input("Konu / çalışma başlığı", existing != null ? existing.title : "");
        final EditText subtitles = input("Alt başlıklar", existing != null ? existing.subtitles : "");
        final Spinner difficulty = spinner(new String[]{"Kolay", "Orta", "Zor"}, existing != null ? existing.difficulty : "Orta");
        final EditText estimated = input("Tahmini çalışma süresi (dk)", existing != null ? String.valueOf(existing.estimatedMinutes) : "45");
        estimated.setInputType(InputType.TYPE_CLASS_NUMBER);
        final Spinner priority = spinner(new String[]{"Düşük", "Orta", "Yüksek"}, existing != null ? existing.priority : "Orta");
        final Spinner status = spinner(new String[]{STATUS_NOT_STARTED, STATUS_IN_PROGRESS, STATUS_DONE}, existing != null ? existing.status : STATUS_NOT_STARTED);
        final EditText notes = input("Notlar", existing != null ? existing.notes : "");
        final EditText source = input("Kaynak linki / PDF adı", existing != null ? existing.source : "");

        LinearLayout form = dialogForm();
        form.addView(title);
        form.addView(subtitles);
        form.addView(label("Zorluk"));
        form.addView(difficulty);
        form.addView(estimated);
        form.addView(label("Öncelik"));
        form.addView(priority);
        form.addView(label("Durum"));
        form.addView(status);
        form.addView(notes);
        form.addView(source);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Başlık Ekle" : "Başlığı Düzenle")
                .setView(scrollWrap(form))
                .setPositiveButton(existing == null ? "Ekle" : "Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = title.getText().toString().trim();
                        if (TextUtils.isEmpty(value)) {
                            Toast.makeText(MainActivity.this, "Başlık gerekli.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Topic topic = existing != null ? existing : new Topic();
                        topic.id = existing != null ? existing.id : nextId("topic");
                        topic.courseId = course.id;
                        topic.title = value;
                        topic.subtitles = subtitles.getText().toString().trim();
                        topic.difficulty = difficulty.getSelectedItem().toString();
                        topic.estimatedMinutes = parseInt(estimated.getText().toString(), 45);
                        topic.priority = priority.getSelectedItem().toString();
                        topic.status = status.getSelectedItem().toString();
                        topic.notes = notes.getText().toString().trim();
                        topic.source = source.getText().toString().trim();
                        topic.completedAt = topic.status.equals(STATUS_DONE) ? nowIsoDate() : "";
                        if (existing == null) {
                            course.topics.add(topic);
                        }
                        saveCourses();
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showQuizCreateFlow() {
        chooseCourse(new CourseCallback() {
            @Override
            public void onCourse(final Course course) {
                showQuizDialog(course, null);
            }
        });
    }

    private void showQuizDialog(final Course course, Topic presetTopic) {
        if (!isPro() && quizzes.size() >= 20) {
            showProLimitDialog("Ücretsiz sürümde temel quiz kullanımı açıktır; sınırsız quiz Pro’da açılacak.");
            return;
        }
        final ArrayList<Topic> topics = course.topics;
        final String[] topicNames = topicNames(topics);
        final Spinner topicSpinner = spinner(topicNames, presetTopic != null ? presetTopic.title : (topicNames.length > 0 ? topicNames[0] : "Genel"));
        final EditText question = input("Soru", "");
        final EditText a = input("A seçeneği", "");
        final EditText b = input("B seçeneği", "");
        final EditText c = input("C seçeneği", "");
        final EditText d = input("D seçeneği", "");
        final Spinner correct = spinner(new String[]{"A", "B", "C", "D"}, "A");

        LinearLayout form = dialogForm();
        form.addView(label("Başlık"));
        form.addView(topicSpinner);
        form.addView(question);
        form.addView(a);
        form.addView(b);
        form.addView(c);
        form.addView(d);
        form.addView(label("Doğru cevap"));
        form.addView(correct);

        new AlertDialog.Builder(this)
                .setTitle("Quiz Sorusu Oluştur")
                .setView(scrollWrap(form))
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TextUtils.isEmpty(question.getText().toString().trim())) {
                            Toast.makeText(MainActivity.this, "Soru metni gerekli.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        QuizQuestion quiz = new QuizQuestion();
                        quiz.id = nextId("quiz");
                        quiz.courseId = course.id;
                        int idx = topicSpinner.getSelectedItemPosition();
                        quiz.topicId = idx >= 0 && idx < topics.size() ? topics.get(idx).id : "";
                        quiz.question = question.getText().toString().trim();
                        quiz.optionA = defaultText(a.getText().toString(), "Doğru");
                        quiz.optionB = defaultText(b.getText().toString(), "Yanlış");
                        quiz.optionC = defaultText(c.getText().toString(), "-");
                        quiz.optionD = defaultText(d.getText().toString(), "-");
                        quiz.correct = correct.getSelectedItem().toString();
                        quizzes.add(quiz);
                        saveQuizzes();
                        selectedTab = TAB_QUIZ;
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showProfileDialog() {
        final EditText name = input("Kullanıcı adı", prefs.getString(KEY_NAME, "Öğrenci"));
        final EditText className = input("Kullanım alanı", prefs.getString(KEY_CLASS, "Genel öğrenme"));
        LinearLayout form = dialogForm();
        form.addView(name);
        form.addView(className);
        new AlertDialog.Builder(this)
                .setTitle("Profil")
                .setView(form)
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.edit()
                                .putString(KEY_NAME, defaultText(name.getText().toString().trim(), "Öğrenci"))
                                .putString(KEY_CLASS, defaultText(className.getText().toString().trim(), "Genel öğrenme"))
                                .apply();
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showGoalDialog() {
        final EditText daily = input("Günlük hedef dakika", String.valueOf(dailyGoal()));
        daily.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText work = input("Varsayılan Pomodoro çalışma dk", String.valueOf(prefs.getInt(KEY_POMODORO_WORK, 25)));
        work.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText rest = input("Varsayılan mola dk", String.valueOf(prefs.getInt(KEY_POMODORO_BREAK, 5)));
        rest.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout form = dialogForm();
        form.addView(daily);
        form.addView(work);
        form.addView(rest);
        new AlertDialog.Builder(this)
                .setTitle("Çalışma Hedefleri")
                .setView(form)
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int workMinutes = parseInt(work.getText().toString(), 25);
                        prefs.edit()
                                .putInt(KEY_DAILY_GOAL, Math.max(30, parseInt(daily.getText().toString(), 150)))
                                .putInt(KEY_POMODORO_WORK, Math.max(5, workMinutes))
                                .putInt(KEY_POMODORO_BREAK, Math.max(1, parseInt(rest.getText().toString(), 5)))
                                .apply();
                        selectedPomodoroMinutes = workMinutes;
                        remainingSeconds = selectedPomodoroMinutes * 60;
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showExportDialog() {
        JSONObject export = new JSONObject();
        try {
            export.put("courses", coursesToJson());
            export.put("tasks", tasksToJson());
            export.put("sessions", sessionsToJson());
            export.put("quizzes", quizzesToJson());
            export.put("results", resultsToJson());
        } catch (JSONException ignored) {
        }
        TextView view = text(export.toString(), 12, Color.rgb(20, 20, 20), Typeface.NORMAL);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Veri Dışa Aktarma")
                .setMessage("Veriler JSON olarak hazır. Play sürümünde dosyaya kaydetme ve yedekleme akışı bağlanacak.")
                .setView(scrollWrap(view))
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void showMissingTopics() {
        ArrayList<String> lines = new ArrayList<>();
        for (Course course : courses) {
            for (Topic topic : course.topics) {
                if (!STATUS_DONE.equals(topic.status)) {
                    lines.add(course.name + " · " + topic.title + " · " + topic.priority + " öncelik");
                }
            }
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Eksik başlık yok. Harika gidiyorsun.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(15, lines.size()); i++) {
            builder.append("• ").append(lines.get(i)).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("Eksik Başlıklar")
                .setMessage(builder.toString())
                .setPositiveButton("Tekrar Planına Ekle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        generateWeeklyPlan();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void showProLimitDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Pro Özellik")
                .setMessage(message + "\n\nPro özellikleri için satın alma ekranı hazır; gerçek Play Billing ürün kimlikleri yayın öncesi bağlanmalı.")
                .setPositiveButton("Pro’ya Git", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedTab = TAB_PROFILE;
                        render();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void chooseCourse(final CourseCallback callback) {
        if (courses.isEmpty()) {
            Toast.makeText(this, "Önce çalışma alanı ekle.", Toast.LENGTH_SHORT).show();
            showCourseDialog(null);
            return;
        }
        String[] names = new String[courses.size()];
        for (int i = 0; i < courses.size(); i++) {
            names[i] = courses.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("Alan Seç")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onCourse(courses.get(which));
                    }
                })
                .show();
    }

    private void chooseTopic(final Course course, final TopicCallback callback) {
        if (course.topics.isEmpty()) {
            Toast.makeText(this, "Bu alana önce başlık ekle.", Toast.LENGTH_SHORT).show();
            showTopicDialog(course, null);
            return;
        }
        String[] names = topicNames(course.topics);
        new AlertDialog.Builder(this)
                .setTitle("Başlık Seç")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onTopic(course.topics.get(which));
                    }
                })
                .show();
    }

    private void generateDailyPlan() {
        int created = generatePlanForCourses(courses, 1, false, "");
        Toast.makeText(this, created + " görev bugünün planına eklendi.", Toast.LENGTH_SHORT).show();
        selectedTab = TAB_PLAN;
        planMode = 0;
        render();
    }

    private void generateWeeklyPlan() {
        int created = generatePlanForCourses(courses, 7, false, "");
        Toast.makeText(this, created + " görev 7 günlük plana eklendi.", Toast.LENGTH_SHORT).show();
        selectedTab = TAB_PLAN;
        planMode = 1;
        render();
    }

    private void generateExamPlanForCourse(Course course) {
        int days = Math.max(1, daysUntil(course.examDate));
        int created = generatePlanForCourses(singleCourseList(course), Math.min(Math.max(days, 1), 30), true, course.id);
        Toast.makeText(this, course.name + " için " + created + " görev oluşturuldu.", Toast.LENGTH_SHORT).show();
        selectedTab = TAB_PLAN;
        planMode = 2;
        render();
    }

    private int generatePlanForCourses(ArrayList<Course> sourceCourses, int days, boolean examPlan, String onlyCourseId) {
        if (sourceCourses.isEmpty()) {
            Toast.makeText(this, "Önce çalışma alanı ekle.", Toast.LENGTH_SHORT).show();
            return 0;
        }
        ArrayList<TopicPlanItem> items = new ArrayList<>();
        for (Course course : sourceCourses) {
            for (Topic topic : course.topics) {
                if (!STATUS_DONE.equals(topic.status)) {
                    items.add(new TopicPlanItem(course, topic, topicScore(course, topic)));
                }
            }
        }
        Collections.sort(items, new Comparator<TopicPlanItem>() {
            @Override
            public int compare(TopicPlanItem left, TopicPlanItem right) {
                return right.score - left.score;
            }
        });
        if (items.isEmpty()) {
            Toast.makeText(this, "Planlanacak eksik başlık yok.", Toast.LENGTH_SHORT).show();
            return 0;
        }

        clearGeneratedTasks(onlyCourseId, days);
        int created = 0;
        int targetMinutes = Math.max(30, dailyGoal());
        int reserveReviewDays = examPlan && days >= 4 ? 2 : 0;
        int studyDays = Math.max(1, days - reserveReviewDays);

        for (int day = 0; day < studyDays && !items.isEmpty(); day++) {
            String date = addDays(today(), day);
            int left = targetMinutes;
            int start = defaultStartMinute(day);
            while (left >= 20 && !items.isEmpty()) {
                TopicPlanItem item = items.remove(0);
                int duration = clamp(item.topic.estimatedMinutes, 25, Math.min(90, left));
                addTask(item.course, item.topic, item.topic.title, date, minutesToTime(start), duration);
                start += duration + 15;
                left -= duration;
                created++;
            }
        }

        if (examPlan && !sourceCourses.isEmpty()) {
            Course course = sourceCourses.get(0);
            int reviewStart = Math.max(0, days - reserveReviewDays);
            if (reserveReviewDays >= 1) {
                addTask(course, null, "Zor başlıklar tekrarı", addDays(today(), reviewStart), "18:00", Math.min(90, targetMinutes));
                created++;
            }
            if (reserveReviewDays >= 2) {
                addTask(course, null, "Quiz + yanlışlar + genel tekrar", addDays(today(), reviewStart + 1), "18:00", Math.min(90, targetMinutes));
                created++;
            }
        }
        saveTasks();
        return created;
    }

    private void clearGeneratedTasks(final String onlyCourseId, int days) {
        String start = today();
        String end = addDays(today(), Math.max(0, days - 1));
        ArrayList<StudyTask> keep = new ArrayList<>();
        for (StudyTask task : tasks) {
            boolean inWindow = task.date.compareTo(start) >= 0 && task.date.compareTo(end) <= 0;
            boolean courseMatches = TextUtils.isEmpty(onlyCourseId) || onlyCourseId.equals(task.courseId);
            if (TASK_DONE.equals(task.status) || !inWindow || !courseMatches) {
                keep.add(task);
            }
        }
        tasks.clear();
        tasks.addAll(keep);
    }

    private void confirmClearFutureTasks() {
        new AlertDialog.Builder(this)
                .setTitle("Planı Temizle")
                .setMessage("Bugünden itibaren tamamlanmamış plan görevleri temizlenecek.")
                .setPositiveButton("Temizle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearGeneratedTasks("", 30);
                        saveTasks();
                        render();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void pullDeferredToToday() {
        int moved = 0;
        String now = today();
        for (StudyTask task : tasks) {
            if (TASK_DEFERRED.equals(task.status) && task.date.compareTo(now) <= 0) {
                task.date = now;
                task.status = TASK_PLANNED;
                moved++;
            }
        }
        saveTasks();
        Toast.makeText(this, moved + " ertelenen görev bugüne taşındı.", Toast.LENGTH_SHORT).show();
        render();
    }

    private void addSingleTask(Course course, Topic topic, String date) {
        int duration = topic != null ? clamp(topic.estimatedMinutes, 25, 90) : 45;
        addTask(course, topic, topic != null ? topic.title : "Genel tekrar", date, "18:00", duration);
        saveTasks();
    }

    private void addTask(Course course, Topic topic, String title, String date, String startTime, int duration) {
        StudyTask task = new StudyTask();
        task.id = nextId("task");
        task.courseId = course.id;
        task.topicId = topic != null ? topic.id : "";
        task.title = title;
        task.date = date;
        task.startTime = startTime;
        task.duration = duration;
        task.status = TASK_PLANNED;
        tasks.add(task);
    }

    private void startTask(StudyTask task) {
        activeCourseId = task.courseId;
        activeTopicId = task.topicId;
        selectedPomodoroMinutes = clamp(task.duration, 15, 90);
        remainingSeconds = selectedPomodoroMinutes * 60;
        timerRunning = false;
        selectedTab = TAB_POMODORO;
        render();
    }

    private void deferTask(StudyTask task) {
        task.date = addDays(task.date, 1);
        task.status = TASK_DEFERRED;
        saveTasks();
        Toast.makeText(this, "Görev yarına ertelendi.", Toast.LENGTH_SHORT).show();
        render();
    }

    private void completeTask(StudyTask task) {
        task.status = TASK_DONE;
        Topic topic = findTopic(task.courseId, task.topicId);
        if (topic != null) {
            topic.status = STATUS_DONE;
            topic.completedAt = nowIsoDate();
            saveCourses();
        }
        saveTasks();
        Toast.makeText(this, "Görev tamamlandı.", Toast.LENGTH_SHORT).show();
        render();
    }

    private void setPomodoroMode(int minutes) {
        if (timerRunning) {
            Toast.makeText(this, "Süre değişimi için önce duraklat.", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedPomodoroMinutes = minutes;
        remainingSeconds = minutes * 60;
        updateTimerLabel();
        render();
    }

    private void toggleTimer() {
        if (timerRunning) {
            timerRunning = false;
            handler.removeCallbacks(timerTick);
        } else {
            if (TextUtils.isEmpty(activeCourseId) && !courses.isEmpty()) {
                activeCourseId = courses.get(0).id;
                if (!courses.get(0).topics.isEmpty()) {
                    activeTopicId = courses.get(0).topics.get(0).id;
                }
            }
            timerRunning = true;
            handler.removeCallbacks(timerTick);
            handler.postDelayed(timerTick, 1000L);
        }
        render();
    }

    private void completePomodoro(boolean naturalFinish) {
        handler.removeCallbacks(timerTick);
        int worked = Math.max(1, selectedPomodoroMinutes - (remainingSeconds / 60));
        PomodoroSession session = new PomodoroSession();
        session.id = nextId("session");
        session.courseId = activeCourseId;
        session.topicId = activeTopicId;
        session.duration = naturalFinish ? selectedPomodoroMinutes : worked;
        session.completed = naturalFinish || worked >= 5;
        session.createdAt = nowMillis();
        sessions.add(session);
        saveSessions();
        remainingSeconds = selectedPomodoroMinutes * 60;
        notifyPomodoroFinished();
        final Topic topic = findTopic(activeCourseId, activeTopicId);
        if (topic != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Pomodoro tamamlandı")
                    .setMessage("Bu başlığı tamamladın mı?\n\n" + topic.title)
                    .setPositiveButton("Tamamlandı", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            topic.status = STATUS_DONE;
                            topic.completedAt = nowIsoDate();
                            saveCourses();
                            render();
                        }
                    })
                    .setNegativeButton("Devam edecek", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            topic.status = STATUS_IN_PROGRESS;
                            saveCourses();
                            render();
                        }
                    })
                    .show();
        } else {
            Toast.makeText(this, "Pomodoro kaydedildi.", Toast.LENGTH_SHORT).show();
            render();
        }
    }

    private void updateTimerLabel() {
        if (timerText != null) {
            timerText.setText(formatTimer(remainingSeconds));
        }
        if (timerCourseText != null) {
            timerCourseText.setText(activeStudyLabel());
        }
    }

    private void solveQuiz(final QuizQuestion quiz, String selected, final Course course, final Topic topic) {
        boolean correct = selected.equals(quiz.correct);
        QuizResult result = new QuizResult();
        result.id = nextId("result");
        result.quizId = quiz.id;
        result.courseId = quiz.courseId;
        result.topicId = quiz.topicId;
        result.correctCount = correct ? 1 : 0;
        result.wrongCount = correct ? 0 : 1;
        result.score = correct ? 100 : 0;
        result.createdAt = nowMillis();
        results.add(result);
        saveResults();
        String message = correct
                ? "Doğru cevap. Bu başlık iyi görünüyor."
                : (topic != null ? topic.title + " için tekrar önerilir. Bu başlığı tekrar planına ekleyelim mi?" : "Yanlış cevap. Genel tekrar önerilir.");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(correct ? "Sonuç: %100" : "Sonuç: %0")
                .setMessage(message)
                .setNegativeButton("Kapat", null);
        if (!correct && course != null) {
            builder.setPositiveButton("Tekrar Planına Ekle", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    addTask(course, topic, topic != null ? topic.title : "Quiz tekrarı", addDays(today(), 1), "18:00", topic != null ? clamp(topic.estimatedMinutes, 25, 60) : 45);
                    saveTasks();
                    Toast.makeText(MainActivity.this, "Tekrar görevi yarına eklendi.", Toast.LENGTH_SHORT).show();
                    render();
                }
            });
        }
        builder.show();
        render();
    }

    private void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME, mode).apply();
        render();
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(colors.bg);
            window.setNavigationBarColor(colors.surface);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (isDark()) {
                window.getDecorView().setSystemUiVisibility(0);
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "DersPilot Odak", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Pomodoro ve çalışma hatırlatıcıları");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 40);
        }
    }

    private void notifyPomodoroFinished() {
        if (!prefs.getBoolean(KEY_NOTIFY_POMODORO, true)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("DersPilot")
                .setContentText("Odak oturumu tamamlandı. Başlığı işaretlemeyi unutma.")
                .setSmallIcon(getApplicationInfo().icon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify(1001, builder.build());
    }

    private void seedPrefs() {
        if (!prefs.contains(KEY_DAILY_GOAL)) {
            prefs.edit()
                    .putString(KEY_NAME, "Öğrenci")
                    .putString(KEY_CLASS, "Genel öğrenme")
                    .putInt(KEY_DAILY_GOAL, 150)
                    .putBoolean(KEY_IS_PRO, false)
                    .putString(KEY_THEME, "system")
                    .putString(KEY_LANGUAGE, "Türkçe")
                    .putString(KEY_WEEK_START, "Pazartesi")
                    .putInt(KEY_POMODORO_WORK, 25)
                    .putInt(KEY_POMODORO_BREAK, 5)
                    .putBoolean(KEY_AUTO_REVIEW, true)
                    .putInt(KEY_EXAM_WARNING, 7)
                    .putBoolean(KEY_NOTIFY_DAILY, true)
                    .putBoolean(KEY_NOTIFY_EXAM, true)
                    .putBoolean(KEY_NOTIFY_PLAN, true)
                    .putBoolean(KEY_NOTIFY_POMODORO, true)
                    .putBoolean(KEY_NOTIFY_MOTIVATION, true)
                    .putBoolean(KEY_SHOW_AD_TEST, false)
                    .apply();
        }
    }

    private void loadData() {
        courses.clear();
        tasks.clear();
        sessions.clear();
        quizzes.clear();
        results.clear();
        try {
            JSONArray courseArray = new JSONArray(prefs.getString(KEY_COURSES, "[]"));
            for (int i = 0; i < courseArray.length(); i++) {
                courses.add(Course.fromJson(courseArray.getJSONObject(i)));
            }
            JSONArray taskArray = new JSONArray(prefs.getString(KEY_TASKS, "[]"));
            for (int i = 0; i < taskArray.length(); i++) {
                tasks.add(StudyTask.fromJson(taskArray.getJSONObject(i)));
            }
            JSONArray sessionArray = new JSONArray(prefs.getString(KEY_SESSIONS, "[]"));
            for (int i = 0; i < sessionArray.length(); i++) {
                sessions.add(PomodoroSession.fromJson(sessionArray.getJSONObject(i)));
            }
            JSONArray quizArray = new JSONArray(prefs.getString(KEY_QUIZZES, "[]"));
            for (int i = 0; i < quizArray.length(); i++) {
                quizzes.add(QuizQuestion.fromJson(quizArray.getJSONObject(i)));
            }
            JSONArray resultArray = new JSONArray(prefs.getString(KEY_RESULTS, "[]"));
            for (int i = 0; i < resultArray.length(); i++) {
                results.add(QuizResult.fromJson(resultArray.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            courses.clear();
            tasks.clear();
            sessions.clear();
            quizzes.clear();
            results.clear();
        }
    }

    private void saveCourses() {
        prefs.edit().putString(KEY_COURSES, coursesToJson().toString()).apply();
    }

    private void saveTasks() {
        prefs.edit().putString(KEY_TASKS, tasksToJson().toString()).apply();
    }

    private void saveSessions() {
        prefs.edit().putString(KEY_SESSIONS, sessionsToJson().toString()).apply();
    }

    private void saveQuizzes() {
        prefs.edit().putString(KEY_QUIZZES, quizzesToJson().toString()).apply();
    }

    private void saveResults() {
        prefs.edit().putString(KEY_RESULTS, resultsToJson().toString()).apply();
    }

    private JSONArray coursesToJson() {
        JSONArray array = new JSONArray();
        for (Course course : courses) {
            array.put(course.toJson());
        }
        return array;
    }

    private JSONArray tasksToJson() {
        JSONArray array = new JSONArray();
        for (StudyTask task : tasks) {
            array.put(task.toJson());
        }
        return array;
    }

    private JSONArray sessionsToJson() {
        JSONArray array = new JSONArray();
        for (PomodoroSession session : sessions) {
            array.put(session.toJson());
        }
        return array;
    }

    private JSONArray quizzesToJson() {
        JSONArray array = new JSONArray();
        for (QuizQuestion quiz : quizzes) {
            array.put(quiz.toJson());
        }
        return array;
    }

    private JSONArray resultsToJson() {
        JSONArray array = new JSONArray();
        for (QuizResult result : results) {
            array.put(result.toJson());
        }
        return array;
    }

    private ArrayList<StudyTask> todayTasks(boolean sort) {
        return tasksForDate(today(), sort);
    }

    private ArrayList<StudyTask> todayTasks(boolean sort, boolean includeDone) {
        ArrayList<StudyTask> found = new ArrayList<>();
        for (StudyTask task : tasks) {
            if (today().equals(task.date) && (includeDone || !TASK_DONE.equals(task.status))) {
                found.add(task);
            }
        }
        if (sort) {
            sortTasks(found);
        }
        return found;
    }

    private ArrayList<StudyTask> tasksForDate(String date) {
        return tasksForDate(date, true);
    }

    private ArrayList<StudyTask> tasksForDate(String date, boolean sort) {
        ArrayList<StudyTask> found = new ArrayList<>();
        for (StudyTask task : tasks) {
            if (date.equals(task.date)) {
                found.add(task);
            }
        }
        if (sort) {
            sortTasks(found);
        }
        return found;
    }

    private void sortTasks(ArrayList<StudyTask> list) {
        Collections.sort(list, new Comparator<StudyTask>() {
            @Override
            public int compare(StudyTask left, StudyTask right) {
                return left.startTime.compareTo(right.startTime);
            }
        });
    }

    private ArrayList<Course> upcomingCourses() {
        ArrayList<Course> found = new ArrayList<>();
        for (Course course : courses) {
            if (!TextUtils.isEmpty(course.examDate)) {
                found.add(course);
            }
        }
        Collections.sort(found, new Comparator<Course>() {
            @Override
            public int compare(Course left, Course right) {
                return left.examDate.compareTo(right.examDate);
            }
        });
        return found;
    }

    private ArrayList<Topic> incompleteTopics(Course course) {
        ArrayList<Topic> found = new ArrayList<>();
        for (Topic topic : course.topics) {
            if (!STATUS_DONE.equals(topic.status)) {
                found.add(topic);
            }
        }
        return found;
    }

    private int todayPlannedMinutes() {
        int total = 0;
        for (StudyTask task : todayTasks(true, true)) {
            total += task.duration;
        }
        return total;
    }

    private int todayDoneMinutes() {
        int total = 0;
        for (StudyTask task : todayTasks(true, true)) {
            if (TASK_DONE.equals(task.status)) {
                total += task.duration;
            }
        }
        String date = today();
        for (PomodoroSession session : sessions) {
            if (date.equals(millisToDate(session.createdAt)) && session.completed) {
                total += session.duration;
            }
        }
        return total;
    }

    private int totalStudyMinutes() {
        int total = 0;
        for (StudyTask task : tasks) {
            if (TASK_DONE.equals(task.status)) {
                total += task.duration;
            }
        }
        for (PomodoroSession session : sessions) {
            if (session.completed) {
                total += session.duration;
            }
        }
        return total;
    }

    private int totalStudyMinutesLastDays(int days) {
        int total = 0;
        String start = addDays(today(), -days + 1);
        for (PomodoroSession session : sessions) {
            String date = millisToDate(session.createdAt);
            if (date.compareTo(start) >= 0 && date.compareTo(today()) <= 0 && session.completed) {
                total += session.duration;
            }
        }
        for (StudyTask task : tasks) {
            if (task.date.compareTo(start) >= 0 && task.date.compareTo(today()) <= 0 && TASK_DONE.equals(task.status)) {
                total += task.duration;
            }
        }
        return total;
    }

    private int completedTopicCount() {
        int count = 0;
        for (Course course : courses) {
            count += completedTopics(course);
        }
        return count;
    }

    private int completedTopics(Course course) {
        int count = 0;
        for (Topic topic : course.topics) {
            if (STATUS_DONE.equals(topic.status)) {
                count++;
            }
        }
        return count;
    }

    private int overallProgress() {
        int total = 0;
        int done = 0;
        for (Course course : courses) {
            total += course.topics.size();
            done += completedTopics(course);
        }
        return total == 0 ? 0 : Math.round(done * 100f / total);
    }

    private int courseProgress(Course course) {
        return course.topics.isEmpty() ? 0 : Math.round(completedTopics(course) * 100f / course.topics.size());
    }

    private int streakDays() {
        int streak = 0;
        for (int i = 0; i < 60; i++) {
            String date = addDays(today(), -i);
            boolean worked = false;
            for (PomodoroSession session : sessions) {
                if (date.equals(millisToDate(session.createdAt)) && session.completed) {
                    worked = true;
                    break;
                }
            }
            for (StudyTask task : tasks) {
                if (date.equals(task.date) && TASK_DONE.equals(task.status)) {
                    worked = true;
                    break;
                }
            }
            if (worked) {
                streak++;
            } else if (i > 0) {
                break;
            } else {
                break;
            }
        }
        return streak;
    }

    private int weeklyGoalMinutes() {
        int total = 0;
        for (Course course : courses) {
            total += course.weeklyGoalHour * 60;
        }
        return total == 0 ? dailyGoal() * 5 : total;
    }

    private String upcomingExamLabel() {
        ArrayList<Course> upcoming = upcomingCourses();
        if (upcoming.isEmpty()) {
            return "Yok";
        }
        Course first = upcoming.get(0);
        return first.name + "\n" + daysUntilText(first.examDate);
    }

    private int topicScore(Course course, Topic topic) {
        int score = 0;
        score += "Yüksek".equals(topic.priority) ? 90 : ("Orta".equals(topic.priority) ? 55 : 25);
        score += "Zor".equals(topic.difficulty) ? 60 : ("Orta".equals(topic.difficulty) ? 35 : 15);
        score += "Zor".equals(course.difficulty) ? 30 : ("Orta".equals(course.difficulty) ? 15 : 5);
        int days = daysUntil(course.examDate);
        if (days >= 0) {
            score += Math.max(0, 45 - Math.min(days, 45));
        }
        score += Math.min(30, topic.estimatedMinutes / 5);
        return score;
    }

    private Course findCourse(String id) {
        for (Course course : courses) {
            if (course.id.equals(id)) {
                return course;
            }
        }
        return null;
    }

    private Topic findTopic(String courseId, String topicId) {
        Course course = findCourse(courseId);
        if (course == null) {
            return null;
        }
        for (Topic topic : course.topics) {
            if (topic.id.equals(topicId)) {
                return topic;
            }
        }
        return null;
    }

    private String activeStudyLabel() {
        Course course = findCourse(activeCourseId);
        Topic topic = findTopic(activeCourseId, activeTopicId);
        if (course == null) {
            return "Alan seçilmedi";
        }
        if (topic == null) {
            return course.name + " · Genel çalışma";
        }
        return course.name + " · " + topic.title;
    }

    private String motivationText() {
        String[] lines = {
                "Ne çalışacağını düşünmek yerine ilk görevi başlat.",
                "Küçük bir başlık bugün, yoğun haftada büyük rahatlık demek.",
                "Zor başlıklar erkenden görünür olunca yönetilebilir hale gelir.",
                "Bugünün planı netse çalışmaya başlamak çok daha kolaydır."
        };
        Calendar calendar = Calendar.getInstance();
        return lines[Math.abs(calendar.get(Calendar.DAY_OF_YEAR)) % lines.length];
    }

    private int dailyGoal() {
        return prefs.getInt(KEY_DAILY_GOAL, 150);
    }

    private boolean isPro() {
        return prefs.getBoolean(KEY_IS_PRO, false);
    }

    private String themeMode() {
        return prefs.getString(KEY_THEME, "system");
    }

    private boolean isDark() {
        String mode = themeMode();
        if ("dark".equals(mode)) {
            return true;
        }
        if ("light".equals(mode)) {
            return false;
        }
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private Palette palette() {
        if (isDark()) {
            return new Palette(
                    Color.rgb(12, 18, 32),
                    Color.rgb(21, 31, 48),
                    Color.rgb(30, 41, 59),
                    Color.rgb(226, 232, 240),
                    Color.rgb(148, 163, 184),
                    Color.rgb(51, 65, 85),
                    Color.rgb(96, 165, 250),
                    Color.rgb(34, 197, 94),
                    Color.rgb(249, 115, 22),
                    Color.rgb(239, 68, 68),
                    Color.rgb(168, 85, 247),
                    Color.rgb(67, 44, 24));
        }
        return new Palette(
                Color.rgb(246, 248, 252),
                Color.WHITE,
                Color.rgb(232, 238, 247),
                Color.rgb(15, 23, 42),
                Color.rgb(100, 116, 139),
                Color.rgb(220, 226, 235),
                Color.rgb(37, 99, 235),
                Color.rgb(22, 163, 74),
                Color.rgb(234, 88, 12),
                Color.rgb(220, 38, 38),
                Color.rgb(124, 58, 237),
                Color.rgb(255, 247, 237));
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(2f, 1.0f);
        return view;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, colors != null ? colors.muted : Color.DKGRAY, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 19, colors.text, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(2));
        return view;
    }

    private TextView pill(String value, int color) {
        TextView pill = text(value, 12, Color.WHITE, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        pill.setBackground(round(color, 8, 0, 0));
        return pill;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(colors.surface, 8, colors.line, 1));
        if (Build.VERSION.SDK_INT >= 21) {
            card.setElevation(isDark() ? 0 : dp(1));
        }
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button actionButton(String label, int bg, View.OnClickListener listener) {
        return actionButton(label, bg, Color.WHITE, listener);
    }

    private Button actionButton(String label, int bg, int fg, View.OnClickListener listener) {
        Button button = smallButton(label, bg, fg);
        button.setTextSize(13);
        button.setOnClickListener(listener);
        return button;
    }

    private Button tinyButton(String label, int bg, View.OnClickListener listener) {
        return tinyButton(label, bg, Color.WHITE, listener);
    }

    private Button tinyButton(String label, int bg, int fg, View.OnClickListener listener) {
        Button button = smallButton(label, bg, fg);
        button.setTextSize(12);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(listener);
        return button;
    }

    private Button subtleButton(String label, View.OnClickListener listener) {
        Button button = smallButton(label, colors.surfaceAlt, colors.text);
        button.setOnClickListener(listener);
        return button;
    }

    private Button segmentButton(String label, boolean active, View.OnClickListener listener) {
        Button button = smallButton(label, active ? colors.primary : colors.surfaceAlt, active ? Color.WHITE : colors.text);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        return button;
    }

    private Button smallButton(String label, int bg, int fg) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(fg);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setBackground(round(bg, 8, 0, 0));
        return button;
    }

    private LinearLayout progressBar(int percent, int accent) {
        int safePercent = clamp(percent, 0, 100);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(round(colors.surfaceAlt, 6, 0, 0));
        View fill = new View(this);
        fill.setBackground(round(accent, 6, 0, 0));
        View rest = new View(this);
        outer.addView(fill, new LinearLayout.LayoutParams(0, dp(8), Math.max(1, safePercent)));
        outer.addView(rest, new LinearLayout.LayoutParams(0, dp(8), Math.max(1, 100 - safePercent)));
        return outer;
    }

    private LinearLayout settingLine(String left, String right) {
        LinearLayout row = row();
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(left, 14, colors.text, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(right, 13, colors.muted, Typeface.NORMAL);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout switchLine(String label, final String key) {
        LinearLayout row = row();
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(label, 14, colors.text, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, false));
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(key, ((Switch) v).isChecked()).apply();
                if (KEY_SHOW_AD_TEST.equals(key)) {
                    render();
                }
            }
        });
        row.addView(sw);
        return row;
    }

    private TextView badgeLine(String title, boolean earned) {
        TextView row = text((earned ? "✓ " : "□ ") + title, 14, earned ? colors.success : colors.muted, Typeface.BOLD);
        row.setPadding(0, dp(7), 0, dp(7));
        return row;
    }

    private void addAdPlacement(LinearLayout parent, String placement) {
        if (isPro() || !prefs.getBoolean(KEY_SHOW_AD_TEST, false)) {
            return;
        }
        LinearLayout ad = card();
        ad.setBackground(round(colors.surfaceAlt, 8, colors.line, 1));
        TextView label = text("Reklam yerleşimi hazır: " + placement, 12, colors.muted, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        ad.addView(label);
        parent.addView(ad, blockParams());
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(false);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setHintTextColor(Color.rgb(120, 120, 120));
        edit.setTextSize(15);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        return edit;
    }

    private Spinner spinner(String[] items, String selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(selected)) {
                spinner.setSelection(i);
                break;
            }
        }
        return spinner;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), 0, dp(8), 0);
        return form;
    }

    private ScrollView scrollWrap(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(child);
        return scroll;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams tightBlockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams cardGapParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams smallBlockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams progressParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        params.setMargins(0, dp(10), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams weightParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(left ? 0 : dp(5), 0, left ? dp(5) : 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String today() {
        return isoDate.format(new Date());
    }

    private String nowIsoDate() {
        return today();
    }

    private String nowMillis() {
        return String.valueOf(System.currentTimeMillis());
    }

    private String addDays(String date, int days) {
        Calendar calendar = Calendar.getInstance();
        Date parsed = parseDate(date);
        if (parsed != null) {
            calendar.setTime(parsed);
        }
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return isoDate.format(calendar.getTime());
    }

    private String shortDateLabel(String date) {
        Date parsed = parseDate(date);
        if (parsed == null) {
            return date;
        }
        return shortDate.format(parsed);
    }

    private Date parseDate(String date) {
        try {
            return isoDate.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }

    private int daysUntil(String date) {
        Date parsed = parseDate(date);
        if (parsed == null) {
            return -1;
        }
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = Calendar.getInstance();
        end.setTime(parsed);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        long diff = end.getTimeInMillis() - start.getTimeInMillis();
        return (int) (diff / (24L * 60L * 60L * 1000L));
    }

    private String daysUntilText(String date) {
        int days = daysUntil(date);
        if (days < 0) {
            return "tarih yok";
        }
        if (days == 0) {
            return "bugün";
        }
        if (days == 1) {
            return "yarın";
        }
        return days + " gün kaldı";
    }

    private String millisToDate(String millis) {
        try {
            return isoDate.format(new Date(Long.parseLong(millis)));
        } catch (Exception e) {
            return today();
        }
    }

    private String minutesToTime(int minuteOfDay) {
        int hour = (minuteOfDay / 60) % 24;
        int minute = minuteOfDay % 60;
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private int defaultStartMinute(int dayOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, dayOffset);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return (day == Calendar.SATURDAY || day == Calendar.SUNDAY) ? 10 * 60 : 18 * 60;
    }

    private String formatMinutes(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        if (hour <= 0) {
            return minute + " dk";
        }
        if (minute == 0) {
            return hour + " sa";
        }
        return hour + " sa " + minute + " dk";
    }

    private String formatTimer(int seconds) {
        int safe = Math.max(0, seconds);
        int minute = safe / 60;
        int second = safe % 60;
        return String.format(Locale.US, "%02d:%02d", minute, second);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String defaultText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + Math.abs((int) (Math.random() * 100000));
    }

    private int colorForLabel(String label) {
        if ("Yeşil".equals(label)) {
            return Color.rgb(22, 163, 74);
        }
        if ("Turuncu".equals(label)) {
            return Color.rgb(234, 88, 12);
        }
        if ("Mor".equals(label)) {
            return Color.rgb(124, 58, 237);
        }
        if ("Kırmızı".equals(label)) {
            return Color.rgb(220, 38, 38);
        }
        return Color.rgb(37, 99, 235);
    }

    private String colorLabel(int color) {
        if (color == Color.rgb(22, 163, 74)) {
            return "Yeşil";
        }
        if (color == Color.rgb(234, 88, 12)) {
            return "Turuncu";
        }
        if (color == Color.rgb(124, 58, 237)) {
            return "Mor";
        }
        if (color == Color.rgb(220, 38, 38)) {
            return "Kırmızı";
        }
        return "Mavi";
    }

    private int difficultyColor(String value) {
        if ("Zor".equals(value)) {
            return colors.danger;
        }
        if ("Orta".equals(value)) {
            return colors.warn;
        }
        return colors.success;
    }

    private int statusColor(String value) {
        if (STATUS_DONE.equals(value) || TASK_DONE.equals(value)) {
            return colors.success;
        }
        if (TASK_DEFERRED.equals(value)) {
            return colors.warn;
        }
        if (STATUS_IN_PROGRESS.equals(value)) {
            return colors.primary;
        }
        return colors.muted;
    }

    private String[] topicNames(ArrayList<Topic> topics) {
        if (topics.isEmpty()) {
            return new String[]{"Genel"};
        }
        String[] names = new String[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            names[i] = topics.get(i).title;
        }
        return names;
    }

    private ArrayList<Course> singleCourseList(Course course) {
        ArrayList<Course> list = new ArrayList<>();
        list.add(course);
        return list;
    }

    private interface CourseCallback {
        void onCourse(Course course);
    }

    private interface TopicCallback {
        void onTopic(Topic topic);
    }

    private static class Palette {
        final int bg;
        final int surface;
        final int surfaceAlt;
        final int text;
        final int muted;
        final int line;
        final int primary;
        final int success;
        final int warn;
        final int danger;
        final int pro;
        final int softWarn;

        Palette(int bg, int surface, int surfaceAlt, int text, int muted, int line, int primary, int success, int warn, int danger, int pro, int softWarn) {
            this.bg = bg;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.text = text;
            this.muted = muted;
            this.line = line;
            this.primary = primary;
            this.success = success;
            this.warn = warn;
            this.danger = danger;
            this.pro = pro;
            this.softWarn = softWarn;
        }
    }

    private static class Course {
        String id = "";
        String name = "";
        int color = Color.rgb(37, 99, 235);
        String teacher = "";
        String examDate = "";
        String examType = "Final";
        String difficulty = "Orta";
        int targetGrade = 80;
        int weeklyGoalHour = 5;
        String description = "";
        String createdAt = "";
        ArrayList<Topic> topics = new ArrayList<>();

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("color", color);
                json.put("teacher", teacher);
                json.put("examDate", examDate);
                json.put("examType", examType);
                json.put("difficulty", difficulty);
                json.put("targetGrade", targetGrade);
                json.put("weeklyGoalHour", weeklyGoalHour);
                json.put("description", description);
                json.put("createdAt", createdAt);
                JSONArray topicArray = new JSONArray();
                for (Topic topic : topics) {
                    topicArray.put(topic.toJson());
                }
                json.put("topics", topicArray);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static Course fromJson(JSONObject json) {
            Course course = new Course();
            course.id = json.optString("id");
            course.name = json.optString("name");
            course.color = json.optInt("color", Color.rgb(37, 99, 235));
            course.teacher = json.optString("teacher");
            course.examDate = json.optString("examDate");
            course.examType = json.optString("examType", "Final");
            course.difficulty = json.optString("difficulty", "Orta");
            course.targetGrade = json.optInt("targetGrade", 80);
            course.weeklyGoalHour = json.optInt("weeklyGoalHour", 5);
            course.description = json.optString("description");
            course.createdAt = json.optString("createdAt");
            JSONArray topicArray = json.optJSONArray("topics");
            if (topicArray != null) {
                for (int i = 0; i < topicArray.length(); i++) {
                    JSONObject topicJson = topicArray.optJSONObject(i);
                    if (topicJson != null) {
                        course.topics.add(Topic.fromJson(topicJson));
                    }
                }
            }
            return course;
        }
    }

    private static class Topic {
        String id = "";
        String courseId = "";
        String title = "";
        String subtitles = "";
        String difficulty = "Orta";
        int estimatedMinutes = 45;
        String priority = "Orta";
        String status = STATUS_NOT_STARTED;
        String notes = "";
        String source = "";
        String completedAt = "";

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("courseId", courseId);
                json.put("title", title);
                json.put("subtitles", subtitles);
                json.put("difficulty", difficulty);
                json.put("estimatedMinutes", estimatedMinutes);
                json.put("priority", priority);
                json.put("status", status);
                json.put("notes", notes);
                json.put("source", source);
                json.put("completedAt", completedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static Topic fromJson(JSONObject json) {
            Topic topic = new Topic();
            topic.id = json.optString("id");
            topic.courseId = json.optString("courseId");
            topic.title = json.optString("title");
            topic.subtitles = json.optString("subtitles");
            topic.difficulty = json.optString("difficulty", "Orta");
            topic.estimatedMinutes = json.optInt("estimatedMinutes", 45);
            topic.priority = json.optString("priority", "Orta");
            topic.status = json.optString("status", STATUS_NOT_STARTED);
            topic.notes = json.optString("notes");
            topic.source = json.optString("source");
            topic.completedAt = json.optString("completedAt");
            return topic;
        }
    }

    private static class StudyTask {
        String id = "";
        String courseId = "";
        String topicId = "";
        String title = "";
        String date = "";
        String startTime = "";
        int duration = 45;
        String status = TASK_PLANNED;

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("courseId", courseId);
                json.put("topicId", topicId);
                json.put("title", title);
                json.put("date", date);
                json.put("startTime", startTime);
                json.put("duration", duration);
                json.put("status", status);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static StudyTask fromJson(JSONObject json) {
            StudyTask task = new StudyTask();
            task.id = json.optString("id");
            task.courseId = json.optString("courseId");
            task.topicId = json.optString("topicId");
            task.title = json.optString("title");
            task.date = json.optString("date");
            task.startTime = json.optString("startTime");
            task.duration = json.optInt("duration", 45);
            task.status = json.optString("status", TASK_PLANNED);
            return task;
        }
    }

    private static class PomodoroSession {
        String id = "";
        String courseId = "";
        String topicId = "";
        int duration = 25;
        boolean completed = true;
        String createdAt = "";

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("courseId", courseId);
                json.put("topicId", topicId);
                json.put("duration", duration);
                json.put("completed", completed);
                json.put("createdAt", createdAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static PomodoroSession fromJson(JSONObject json) {
            PomodoroSession session = new PomodoroSession();
            session.id = json.optString("id");
            session.courseId = json.optString("courseId");
            session.topicId = json.optString("topicId");
            session.duration = json.optInt("duration", 25);
            session.completed = json.optBoolean("completed", true);
            session.createdAt = json.optString("createdAt");
            return session;
        }
    }

    private static class QuizQuestion {
        String id = "";
        String courseId = "";
        String topicId = "";
        String question = "";
        String optionA = "";
        String optionB = "";
        String optionC = "";
        String optionD = "";
        String correct = "A";

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("courseId", courseId);
                json.put("topicId", topicId);
                json.put("question", question);
                json.put("optionA", optionA);
                json.put("optionB", optionB);
                json.put("optionC", optionC);
                json.put("optionD", optionD);
                json.put("correct", correct);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static QuizQuestion fromJson(JSONObject json) {
            QuizQuestion quiz = new QuizQuestion();
            quiz.id = json.optString("id");
            quiz.courseId = json.optString("courseId");
            quiz.topicId = json.optString("topicId");
            quiz.question = json.optString("question");
            quiz.optionA = json.optString("optionA");
            quiz.optionB = json.optString("optionB");
            quiz.optionC = json.optString("optionC");
            quiz.optionD = json.optString("optionD");
            quiz.correct = json.optString("correct", "A");
            return quiz;
        }
    }

    private static class QuizResult {
        String id = "";
        String quizId = "";
        String courseId = "";
        String topicId = "";
        int correctCount = 0;
        int wrongCount = 0;
        int score = 0;
        String createdAt = "";

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("quizId", quizId);
                json.put("courseId", courseId);
                json.put("topicId", topicId);
                json.put("correctCount", correctCount);
                json.put("wrongCount", wrongCount);
                json.put("score", score);
                json.put("createdAt", createdAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static QuizResult fromJson(JSONObject json) {
            QuizResult result = new QuizResult();
            result.id = json.optString("id");
            result.quizId = json.optString("quizId");
            result.courseId = json.optString("courseId");
            result.topicId = json.optString("topicId");
            result.correctCount = json.optInt("correctCount", 0);
            result.wrongCount = json.optInt("wrongCount", 0);
            result.score = json.optInt("score", 0);
            result.createdAt = json.optString("createdAt");
            return result;
        }
    }

    private static class TopicPlanItem {
        final Course course;
        final Topic topic;
        final int score;

        TopicPlanItem(Course course, Topic topic, int score) {
            this.course = course;
            this.topic = topic;
            this.score = score;
        }
    }
}
