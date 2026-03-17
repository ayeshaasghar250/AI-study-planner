import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.*;

// ── Model ────────────────────────────────────────────────────
class User {
    private String username, password;
    public User(String u,String p){username=u;password=p;}
    public String getUsername(){return username;}
    public boolean checkPassword(String p){return password.equals(p);}
}
class Subject {
    private String name; private int difficulty; private LocalDate examDate;
    static final Color[] PALETTE = {
            new Color(96,165,250), new Color(167,139,250), new Color(52,211,153),
            new Color(251,191,36), new Color(249,115,22),  new Color(236,72,153),
            new Color(20,184,166), new Color(132,204,22)
    };
    public Subject(String n,int d,LocalDate e){name=n;examDate=e;difficulty=(d>=1&&d<=5)?d:3;}
    public String getName(){return name;}
    public int getDifficulty(){return difficulty;}
    public LocalDate getExamDate(){return examDate;}
    public Color getColor(){return PALETTE[Math.abs(name.hashCode())%PALETTE.length];}
    public String getDiffLabel(){return new String[]{"","Easy","Moderate","Medium","Hard","Very Hard"}[difficulty];}
    public long daysLeft(){return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),examDate);}
}
class Student extends User {
    private List<Subject> subjects=new ArrayList<>(); private double dailyHours;
    public Student(String u,String p,double h){super(u,p);dailyHours=h;}
    public void addSubject(Subject s){subjects.add(s);}
    public List<Subject> getSubjects(){return new ArrayList<>(subjects);}
    public double getDailyHours(){return dailyHours;}
}
class StudyTask {
    private Subject subject; private LocalDate date; private double hours; private boolean done=false;
    public StudyTask(Subject s,LocalDate d,double h){subject=s;date=d;hours=h;}
    public Subject getSubject(){return subject;} public LocalDate getDate(){return date;}
    public double getHours(){return hours;} public boolean isDone(){return done;}
    public void setDone(boolean d){done=d;}
    public boolean isOverdue(){return !done&&date.isBefore(LocalDate.now());}
    public boolean isToday(){return date.equals(LocalDate.now());}
}
class ScheduleGenerator {
    public List<StudyTask> generate(Student st){
        List<StudyTask> tasks=new ArrayList<>();
        List<Subject> subs=st.getSubjects(); if(subs.isEmpty())return tasks;
        for(Subject s:subs){
            LocalDate today=LocalDate.now(),exam=s.getExamDate();
            if(exam.isBefore(today))continue;
            long days=java.time.temporal.ChronoUnit.DAYS.between(today,exam);
            if(days==0){tasks.add(new StudyTask(s,today,2.0));continue;}
            double base=st.getDailyHours()/subs.size();
            double[]m={0.6,0.8,1.0,1.3,1.6};
            double sh=Math.min(4,Math.max(0.5,Math.round(base*m[s.getDifficulty()-1]*2)/2.0));
            int gap=(days<=14||s.getDifficulty()>=4)?1:(days<=30?2:3);
            for(LocalDate d=today;d.isBefore(exam);d=d.plusDays(gap))
                tasks.add(new StudyTask(s,d,sh));
        }
        tasks.sort(Comparator.comparing(StudyTask::getDate));
        return tasks;
    }
}
class ProgressTracker {
    public double overall(List<StudyTask> t){
        if(t.isEmpty())return 0;
        return(double)t.stream().filter(StudyTask::isDone).count()/t.size()*100;
    }
    public double hoursStudied(List<StudyTask> t){
        return t.stream().filter(StudyTask::isDone).mapToDouble(StudyTask::getHours).sum();
    }
    public long overdue(List<StudyTask> t){return t.stream().filter(StudyTask::isOverdue).count();}
    public Map<String,Double> perSubject(List<StudyTask> tasks){
        Map<String,Double> m=new LinkedHashMap<>();
        tasks.stream().collect(Collectors.groupingBy(t->t.getSubject().getName()))
                .forEach((k,v)->m.put(k,(double)v.stream().filter(StudyTask::isDone).count()/v.size()*100));
        return m;
    }
}

// ── Main App ─────────────────────────────────────────────────
public class StudyPlanner {

    // VS Code / Notion dark navy palette
    static final Color BG        = new Color(15, 17, 26);   // deepest bg
    static final Color SURFACE   = new Color(20, 23, 35);   // sidebar / panels
    static final Color CARD      = new Color(26, 29, 44);   // cards
    static final Color CARD_HVR  = new Color(30, 34, 52);   // card hover
    static final Color BORDER    = new Color(38, 42, 62);   // borders
    static final Color BORDER2   = new Color(50, 55, 80);   // stronger border
    static final Color ACCENT    = new Color(79, 140, 246); // blue accent (VS Code blue)
    static final Color ACCENT_DIM= new Color(79, 140, 246, 40);
    static final Color TEXT      = new Color(220, 224, 238);
    static final Color TEXT2     = new Color(148, 155, 186);
    static final Color TEXT3     = new Color(80,  88, 120);
    static final Color SUCCESS   = new Color(72, 199, 142);
    static final Color DANGER    = new Color(237, 100, 90);
    static final Color WARNING   = new Color(240, 185, 60);
    static final Color TAG_BG    = new Color(79, 140, 246, 22);
    static final Color ROW_ODD   = new Color(20, 23, 35);
    static final Color ROW_EVEN  = new Color(23, 26, 40);
    static final Color SEL_ROW   = new Color(79, 140, 246, 35);

    static Map<String,Student> users    = new HashMap<>();
    static Student              me;
    static List<StudyTask>      tasks   = new ArrayList<>();
    static ScheduleGenerator    gen     = new ScheduleGenerator();
    static ProgressTracker      tracker = new ProgressTracker();
    static JFrame               frame;
    static JPanel               root;
    static CardLayout           cards;

    static final String LOGIN="L",DASH="D",ADD="A",SCHED="S",PROG="P";

    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            users.put("demo",new Student("demo","1234",6.0));
            frame=new JFrame("Study Planner  v1.0");
            frame.setSize(1200,740);
            frame.setMinimumSize(new Dimension(980,640));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(BG);
            cards=new CardLayout(); root=new JPanel(cards);
            root.add(loginScreen(),LOGIN);
            root.add(new JPanel(),DASH);
            frame.add(root); frame.setVisible(true);
        });
    }

    static void go(String screen){
        tasks=gen.generate(me);
        root.removeAll();
        root.add(loginScreen(), LOGIN);
        root.add(dashboard(),   DASH);
        root.add(addSubject(),  ADD);
        root.add(schedule(),    SCHED);
        root.add(progress(),    PROG);
        root.revalidate();
        cards.show(root,screen);
    }

    // ════════════════════════════════════════════════
    // LOGIN
    // ════════════════════════════════════════════════
    static JPanel loginScreen(){
        // Full split: left panel + right form
        JPanel root2=new JPanel(new GridLayout(1,2,0,0));
        root2.setBackground(BG);

        // ── LEFT: visual branding panel ──────────────
        JPanel left=new JPanel(){
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                // Navy gradient bg
                GradientPaint gp=new GradientPaint(0,0,new Color(15,20,45),0,getHeight(),new Color(10,12,28));
                g2.setPaint(gp); g2.fillRect(0,0,getWidth(),getHeight());
                // Right border
                g2.setColor(BORDER); g2.drawLine(getWidth()-1,0,getWidth()-1,getHeight());
                // Decorative large circle
                g2.setColor(new Color(79,140,246,18));
                g2.fillOval(-80,getHeight()/2-200,380,380);
                // Small circles
                g2.setColor(new Color(79,140,246,25));
                g2.fillOval(getWidth()-120,40,160,160);
                g2.setColor(new Color(72,199,142,15));
                g2.fillOval(60,getHeight()-200,200,200);
                g2.dispose();
            }
        };
        left.setLayout(new GridBagLayout());
        left.setBackground(new Color(15,20,45));

        JPanel leftContent=new JPanel();
        leftContent.setLayout(new BoxLayout(leftContent,BoxLayout.Y_AXIS));
        leftContent.setOpaque(false);
        leftContent.setBorder(BorderFactory.createEmptyBorder(0,48,0,48));

        // Big "S" logo
        JPanel bigLogo=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(79,140,246,50));
                g2.fillOval(0,0,72,72);
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(0,0,71,71);
                g2.setColor(TEXT);
                g2.setFont(new Font("Segoe UI",Font.BOLD,30));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString("S",(72-fm.stringWidth("S"))/2,(72-fm.getHeight())/2+fm.getAscent());
                g2.dispose();
            }
        };
        bigLogo.setPreferredSize(new Dimension(72,72));
        bigLogo.setMaximumSize(new Dimension(72,72));
        bigLogo.setOpaque(false); bigLogo.setAlignmentX(0f);

        JLabel brand=mk("Study Planner",28,Font.BOLD,TEXT); brand.setAlignmentX(0f);
        JLabel tagline=mk("Organize · Focus · Succeed",14,Font.PLAIN,TEXT2); tagline.setAlignmentX(0f);

        // Divider
        JPanel div=new JPanel(); div.setOpaque(false);
        div.setMaximumSize(new Dimension(40,2));
        div.setPreferredSize(new Dimension(40,2));
        div.setBackground(ACCENT);

        // Feature bullets
        String[][] features={
                {"Plan Smart","Auto-generates schedule based on difficulty"},
                {"Track Progress","See completion stats for every subject"},
                {"Stay on Time","Color-coded urgency for upcoming exams"}
        };
        JPanel featList=new JPanel(); featList.setLayout(new BoxLayout(featList,BoxLayout.Y_AXIS));
        featList.setOpaque(false);
        for(String[] f:features){
            JPanel row=new JPanel(new BorderLayout(14,0)); row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));
            // dot
            JPanel dotP=new JPanel(){
                protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(79,140,246,40)); g2.fillOval(0,4,20,20);
                    g2.setColor(ACCENT); g2.fillOval(5,9,10,10);
                    g2.dispose();
                }
            };
            dotP.setPreferredSize(new Dimension(20,28)); dotP.setOpaque(false);
            JPanel txt=new JPanel(); txt.setLayout(new BoxLayout(txt,BoxLayout.Y_AXIS)); txt.setOpaque(false);
            txt.add(mk(f[0],13,Font.BOLD,TEXT));
            txt.add(mk(f[1],11,Font.PLAIN,TEXT2));
            row.add(dotP,BorderLayout.WEST); row.add(txt,BorderLayout.CENTER);
            featList.add(row); featList.add(vsp(12));
        }

        leftContent.add(bigLogo);   leftContent.add(vsp(20));
        leftContent.add(brand);     leftContent.add(vsp(6));
        leftContent.add(tagline);   leftContent.add(vsp(28));
        // manual divider line
        JPanel divLine=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(79,140,246,80)); g2.fillRect(0,0,40,2); g2.dispose();
            }
        };
        divLine.setPreferredSize(new Dimension(40,2)); divLine.setMaximumSize(new Dimension(40,2));
        divLine.setOpaque(false); divLine.setAlignmentX(0f);
        leftContent.add(divLine);   leftContent.add(vsp(28));
        leftContent.add(featList);
        left.add(leftContent);

        // ── RIGHT: login form ────────────────────────
        JPanel right=new JPanel(new GridBagLayout());
        right.setBackground(BG);

        JPanel form=new JPanel();
        form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
        form.setBackground(BG);
        form.setPreferredSize(new Dimension(360,480));

        JLabel title=mk("Sign in",24,Font.BOLD,TEXT); title.setAlignmentX(0f);
        JLabel sub=mkL("Enter your credentials to continue",13,TEXT2);

        JLabel uLbl=mkL("Username",12,TEXT2); JTextField uF=tf(); placeholder(uF,"e.g. ayesha123");
        JLabel pLbl=mkL("Password",12,TEXT2);
        JPasswordField pF=new JPasswordField(); styleTf(pF); pF.putClientProperty("JPasswordField.cutCopyAllowed",false);
        JLabel status=mkL(" ",12,DANGER);

        JButton loginBtn=primaryBtn("Sign in");
        JButton regBtn=secondaryBtn("Create account");

        loginBtn.addActionListener(e->{
            String u=uF.getText().trim(); String p=new String(pF.getPassword());
            Student st=users.get(u.toLowerCase());
            if(st!=null&&st.checkPassword(p)){me=st;go(DASH);}
            else status.setText("Incorrect username or password");
        });
        regBtn.addActionListener(e->regDialog(status));
        pF.addKeyListener(new KeyAdapter(){
            public void keyPressed(KeyEvent e){if(e.getKeyCode()==KeyEvent.VK_ENTER)loginBtn.doClick();}
        });
        JLabel hint=mkL("Demo: username = demo  ·  password = 1234",11,TEXT3);

        form.add(title);      form.add(vsp(4));
        form.add(sub);        form.add(vsp(30));
        form.add(uLbl);       form.add(vsp(6));
        form.add(uF);         form.add(vsp(16));
        form.add(pLbl);       form.add(vsp(6));
        form.add(pF);         form.add(vsp(22));
        form.add(loginBtn);   form.add(vsp(10));
        form.add(regBtn);     form.add(vsp(18));
        form.add(status);     form.add(vsp(8));
        form.add(hint);

        right.add(form);
        root2.add(left); root2.add(right);
        return root2;
    }

    static void regDialog(JLabel ref){
        JDialog dlg=new JDialog(frame,"New Account",true);
        dlg.setSize(420,500); dlg.setLocationRelativeTo(frame); dlg.setResizable(false);
        JPanel p=new JPanel(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createEmptyBorder(30,34,30,34));
        p.add(mk("Create Account",18,Font.BOLD,TEXT)); p.add(vsp(4));
        p.add(mkL("Fill in your details below",12,TEXT2)); p.add(vsp(24));
        p.add(mkL("Username",12,TEXT2)); p.add(vsp(5));
        JTextField uF=tf(); p.add(uF); p.add(vsp(14));
        p.add(mkL("Password",12,TEXT2)); p.add(vsp(5));
        JPasswordField pF=new JPasswordField(); styleTf(pF); p.add(pF); p.add(vsp(14));
        p.add(mkL("Confirm Password",12,TEXT2)); p.add(vsp(5));
        JPasswordField cF=new JPasswordField(); styleTf(cF); p.add(cF); p.add(vsp(14));
        p.add(mkL("Daily Study Hours",12,TEXT2)); p.add(vsp(5));
        JPanel hRow=new JPanel(new BorderLayout(10,0)); hRow.setBackground(CARD);
        hRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,34)); hRow.setAlignmentX(0f);
        JSlider hs=new JSlider(1,12,4); hs.setBackground(CARD);
        hs.setMajorTickSpacing(3); hs.setPaintTicks(true); hs.setSnapToTicks(true);
        JLabel hv=mk("4 hrs",13,Font.BOLD,ACCENT);
        hs.addChangeListener(e->hv.setText(hs.getValue()+" hrs"));
        hRow.add(hs,BorderLayout.CENTER); hRow.add(hv,BorderLayout.EAST);
        p.add(hRow); p.add(vsp(20));
        JLabel err=mkL(" ",12,DANGER); p.add(err); p.add(vsp(10));
        JPanel bRow=new JPanel(new GridLayout(1,2,10,0));
        bRow.setBackground(CARD); bRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,40)); bRow.setAlignmentX(0f);
        JButton cancel=secondaryBtn("Cancel"); cancel.addActionListener(e->dlg.dispose());
        JButton create=primaryBtn("Create");
        create.addActionListener(e->{
            String u=uF.getText().trim().toLowerCase();
            String pw=new String(pF.getPassword()),cf=new String(cF.getPassword());
            if(u.length()<3){err.setText("Username min 3 chars");return;}
            if(pw.length()<4){err.setText("Password min 4 chars");return;}
            if(!pw.equals(cf)){err.setText("Passwords don't match");return;}
            if(users.containsKey(u)){err.setText("Username taken");return;}
            users.put(u,new Student(u,pw,hs.getValue()));
            dlg.dispose(); ref.setForeground(SUCCESS);
            ref.setText("Account created — sign in now");
        });
        bRow.add(cancel); bRow.add(create); p.add(bRow);
        dlg.add(p); dlg.setVisible(true);
    }

    // ════════════════════════════════════════════════
    // SIDEBAR
    // ════════════════════════════════════════════════
    static JPanel sidebar(String active){
        JPanel sb=new JPanel();
        sb.setLayout(new BoxLayout(sb,BoxLayout.Y_AXIS));
        sb.setBackground(SURFACE);
        sb.setPreferredSize(new Dimension(220,0));
        sb.setBorder(BorderFactory.createMatteBorder(0,0,0,1,BORDER));

        // App logo
        JPanel logo=new JPanel(new FlowLayout(FlowLayout.LEFT,16,16));
        logo.setBackground(SURFACE);
        JPanel dot=colorDot(ACCENT,8); logo.add(dot);
        logo.add(mk("Study Planner",14,Font.BOLD,TEXT));
        sb.add(logo);
        sb.add(hline());

        // User section
        JPanel userSec=new JPanel();
        userSec.setLayout(new BoxLayout(userSec,BoxLayout.Y_AXIS));
        userSec.setBackground(SURFACE);
        userSec.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));
        // User badge row
        JPanel badge=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        badge.setBackground(SURFACE);
        // initials circle
        String ini=me.getUsername().substring(0,Math.min(2,me.getUsername().length())).toUpperCase();
        JPanel circle=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TAG_BG); g2.fillOval(0,0,36,36);
                g2.setColor(ACCENT); g2.setFont(new Font("Segoe UI",Font.BOLD,13));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(ini,(36-fm.stringWidth(ini))/2,(36-fm.getHeight())/2+fm.getAscent());
                g2.dispose();
            }
        };
        circle.setPreferredSize(new Dimension(36,36)); circle.setMaximumSize(new Dimension(36,36));
        circle.setOpaque(false);
        JPanel info=new JPanel(); info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS)); info.setBackground(SURFACE);
        info.add(mk(me.getUsername(),13,Font.BOLD,TEXT));
        info.add(mk(me.getDailyHours()+"h / day",11,Font.PLAIN,TEXT2));
        badge.add(circle); badge.add(info);
        userSec.add(badge);
        sb.add(userSec);
        sb.add(hline());
        sb.add(vsp(6));

        // Nav items — Notion style
        sb.add(navRow("Dashboard",   DASH,  active));
        sb.add(navRow("Add Subject", ADD,   active));
        sb.add(navRow("Schedule",    SCHED, active));
        sb.add(navRow("Progress",    PROG,  active));

        sb.add(Box.createVerticalGlue());
        sb.add(hline());

        JPanel logoutRow=new JPanel(new FlowLayout(FlowLayout.LEFT,16,12));
        logoutRow.setBackground(SURFACE);
        JButton lo=flatBtn("Sign out", TEXT3);
        lo.addActionListener(e->{
            me=null; tasks.clear();
            root.removeAll(); root.add(loginScreen(),LOGIN); root.add(new JPanel(),DASH);
            root.revalidate(); cards.show(root,LOGIN);
        });
        logoutRow.add(lo); sb.add(logoutRow);
        return sb;
    }

    static JPanel navRow(String label,String screen,String active){
        boolean isActive=screen.equals(active);
        JPanel p=new JPanel(new BorderLayout());
        p.setBackground(SURFACE);
        p.setMaximumSize(new Dimension(220,36));
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Active left accent bar
        JPanel accent=new JPanel();
        accent.setBackground(isActive ? ACCENT : SURFACE);
        accent.setPreferredSize(new Dimension(3,0));
        p.add(accent,BorderLayout.WEST);
        Color bgCol = isActive ? CARD_HVR : SURFACE;
        Color fgCol = isActive ? TEXT      : TEXT2;
        JPanel inner=new JPanel(new FlowLayout(FlowLayout.LEFT,14,8));
        inner.setBackground(bgCol);
        JLabel l=mk(label, 13, isActive?Font.BOLD:Font.PLAIN, fgCol);
        inner.add(l);
        p.add(inner,BorderLayout.CENTER);
        if(!isActive){
            p.addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){ inner.setBackground(CARD_HVR); l.setForeground(TEXT); }
                public void mouseExited(MouseEvent e) { inner.setBackground(SURFACE);  l.setForeground(TEXT2);}
                public void mouseClicked(MouseEvent e){ cards.show(root,screen); }
            });
        } else {
            p.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){ cards.show(root,screen); }
            });
        }
        return p;
    }

    // ════════════════════════════════════════════════
    // DASHBOARD
    // ════════════════════════════════════════════════
    static JPanel dashboard(){
        JPanel root2=new JPanel(new BorderLayout());
        root2.setBackground(BG); root2.add(sidebar(DASH),BorderLayout.WEST);
        JScrollPane sp=new JScrollPane(dashContent());
        sp.setBorder(null); sp.setBackground(BG); sp.getViewport().setBackground(BG);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        root2.add(sp,BorderLayout.CENTER); return root2;
    }

    static JPanel dashContent(){
        JPanel c=new JPanel(); c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setBackground(BG); c.setBorder(BorderFactory.createEmptyBorder(32,32,40,32));

        // ── Page title ──────────────────────────────
        c.add(mk("Dashboard",24,Font.BOLD,TEXT));
        c.add(vsp(4));
        c.add(mkL("Welcome back, "+me.getUsername()+" — "+LocalDate.now(),13,Font.PLAIN,TEXT2));
        c.add(vsp(26));

        // ── 4 stat tiles ────────────────────────────
        long done=tasks.stream().filter(StudyTask::isDone).count();
        long overdue=tracker.overdue(tasks);
        double pct=tracker.overall(tasks);
        double hrs=tracker.hoursStudied(tasks);

        JPanel tiles=new JPanel(new GridLayout(1,4,12,0));
        tiles.setBackground(BG); tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE,96));
        tiles.add(tile("Tasks",       String.valueOf(tasks.size()), ACCENT));
        tiles.add(tile("Completed",   String.valueOf(done),         SUCCESS));
        tiles.add(tile("Overdue",     String.valueOf(overdue),      overdue>0?DANGER:SUCCESS));
        tiles.add(tile("Hrs Studied", String.format("%.1f",hrs),   WARNING));
        c.add(tiles); c.add(vsp(20));

        // ── Progress bar ─────────────────────────────
        JPanel progPanel=notionCard();
        JPanel ptop=new JPanel(new BorderLayout()); ptop.setBackground(CARD);
        ptop.add(mk("Overall Progress",13,Font.BOLD,TEXT),BorderLayout.WEST);
        ptop.add(mk(String.format("%.0f%%  —  %d of %d tasks",pct,done,tasks.size()),12,Font.PLAIN,TEXT2),BorderLayout.EAST);
        double pf=pct;
        JPanel bar=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BORDER); g2.fillRoundRect(0,0,getWidth(),8,8,8);
                int w=Math.max(0,(int)(getWidth()*pf/100));
                if(w>0){g2.setColor(ACCENT); g2.fillRoundRect(0,0,w,8,8,8);}
                g2.dispose();
            }
        };
        bar.setOpaque(false); bar.setPreferredSize(new Dimension(0,8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE,8));
        progPanel.add(ptop); progPanel.add(vsp(12)); progPanel.add(bar);
        c.add(progPanel); c.add(vsp(20));

        // ── 2 col: Today | Exams ─────────────────────
        JPanel cols=new JPanel(new GridLayout(1,2,14,0));
        cols.setBackground(BG); cols.setMaximumSize(new Dimension(Integer.MAX_VALUE,320));

        JPanel todayCard=notionCard();
        todayCard.add(sectionTitle("Today's Sessions")); todayCard.add(vsp(12));
        List<StudyTask> todayT=tasks.stream().filter(StudyTask::isToday).collect(Collectors.toList());
        if(todayT.isEmpty()) todayCard.add(emptyMsgWithBtn("No sessions scheduled for today","View Schedule",SCHED));
        else for(StudyTask t:todayT){ todayCard.add(sessionRow(t)); todayCard.add(vsp(5)); }
        todayCard.add(Box.createVerticalGlue());

        JPanel examCard=notionCard();
        examCard.add(sectionTitle("Upcoming Exams")); examCard.add(vsp(12));
        List<Subject> sorted=me.getSubjects().stream()
                .filter(s->s.daysLeft()>=0).sorted(Comparator.comparingLong(Subject::daysLeft))
                .collect(Collectors.toList());
        if(sorted.isEmpty()) examCard.add(emptyMsgWithBtn("No exams added yet","Add Subject",ADD));
        else for(Subject s:sorted){ examCard.add(examRow(s)); examCard.add(vsp(5)); }
        examCard.add(Box.createVerticalGlue());

        cols.add(todayCard); cols.add(examCard);
        c.add(cols); c.add(vsp(20));

        // ── Subjects row ─────────────────────────────
        JPanel subjCard=notionCard();
        JPanel subHdr=new JPanel(new BorderLayout()); subHdr.setBackground(CARD);
        subHdr.add(sectionTitle("My Subjects ("+me.getSubjects().size()+")"),BorderLayout.WEST);
        JButton addQ=tinyBtn("+ Add Subject");
        addQ.addActionListener(e->cards.show(root,ADD));
        subHdr.add(addQ,BorderLayout.EAST);
        subjCard.add(subHdr); subjCard.add(vsp(14));

        List<Subject> subs=me.getSubjects();
        if(subs.isEmpty()){
            subjCard.add(emptyMsgWithBtn("No subjects yet","Add your first subject",ADD));
        } else {
            JPanel grid=new JPanel(new GridLayout(0,3,10,10));
            grid.setBackground(CARD); grid.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));
            for(Subject s:subs) grid.add(subjectChip(s));
            subjCard.add(grid);
        }
        c.add(subjCard);
        return c;
    }

    // ════════════════════════════════════════════════
    // ADD SUBJECT
    // ════════════════════════════════════════════════
    static JPanel addSubject(){
        JPanel root2=new JPanel(new BorderLayout());
        root2.setBackground(BG); root2.add(sidebar(ADD),BorderLayout.WEST);
        JPanel content=new JPanel(new BorderLayout(0,20));
        content.setBackground(BG); content.setBorder(BorderFactory.createEmptyBorder(32,32,32,32));
        content.add(pageHeader("Add Subject","Add your courses and configure exam dates"),BorderLayout.NORTH);

        JPanel split=new JPanel(new GridLayout(1,2,16,0)); split.setBackground(BG);

        // Form
        JPanel form=notionCard();
        form.add(sectionTitle("Subject Details")); form.add(vsp(18));
        form.add(mkL("Name",12,TEXT2)); form.add(vsp(5));
        JTextField nameF=tf(); placeholder(nameF,"e.g. Mathematics"); form.add(nameF); form.add(vsp(16));
        form.add(mkL("Difficulty",12,TEXT2)); form.add(vsp(5));
        JSlider sl=new JSlider(1,5,3); sl.setBackground(CARD);
        sl.setMajorTickSpacing(1); sl.setPaintTicks(true); sl.setSnapToTicks(true);
        sl.setMaximumSize(new Dimension(Integer.MAX_VALUE,40)); sl.setAlignmentX(0f);
        JLabel diffL=mkL("Medium (3/5)",12,ACCENT);
        sl.addChangeListener(e->{
            String[]ls={"","Easy","Moderate","Medium","Hard","Very Hard"};
            diffL.setText(ls[sl.getValue()]+" ("+sl.getValue()+"/5)");
        });
        form.add(sl); form.add(vsp(4)); form.add(diffL); form.add(vsp(16));
        form.add(mkL("Exam Date  (YYYY-MM-DD)",12,TEXT2)); form.add(vsp(5));
        JTextField dateF=tf(); dateF.setText(LocalDate.now().plusDays(30).toString());
        form.add(dateF); form.add(vsp(4));
        form.add(mkL("e.g.  "+LocalDate.now().plusDays(21),11,TEXT3)); form.add(vsp(22));
        JLabel statusL=mkL(" ",12,TEXT2);
        JButton saveBtn=primaryBtn("Add Subject");

        // List
        JPanel listCard=notionCard();
        listCard.add(sectionTitle("Added Subjects")); listCard.add(vsp(12));
        JPanel listInner=new JPanel(); listInner.setLayout(new BoxLayout(listInner,BoxLayout.Y_AXIS));
        listInner.setBackground(CARD);
        JScrollPane ls=new JScrollPane(listInner); ls.setBorder(null);
        ls.getViewport().setBackground(CARD); ls.setAlignmentX(0f);
        ls.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));

        Runnable ref=()->{
            listInner.removeAll();
            List<Subject> s2=me.getSubjects();
            if(s2.isEmpty()) listInner.add(emptyMsg("No subjects yet"));
            else for(Subject s:s2){ listInner.add(subjectListRow(s)); listInner.add(vsp(6)); }
            listInner.revalidate(); listInner.repaint();
        };
        ref.run();

        saveBtn.addActionListener(e->{
            String name=nameF.getText().trim(), ds=dateF.getText().trim();
            if(name.isEmpty()){statusL.setForeground(DANGER);statusL.setText("Enter a name");return;}
            LocalDate exam;
            try{exam=LocalDate.parse(ds);}
            catch(DateTimeParseException ex){statusL.setForeground(DANGER);statusL.setText("Use YYYY-MM-DD");return;}
            if(exam.isBefore(LocalDate.now())){statusL.setForeground(DANGER);statusL.setText("Date must be future");return;}
            me.addSubject(new Subject(name,sl.getValue(),exam));
            tasks=gen.generate(me); statusL.setForeground(SUCCESS); statusL.setText("Added: "+name);
            nameF.setText(""); sl.setValue(3); dateF.setText(LocalDate.now().plusDays(30).toString());
            ref.run(); go(ADD);
        });

        form.add(saveBtn); form.add(vsp(8)); form.add(statusL); form.add(Box.createVerticalGlue());
        listCard.add(ls);
        split.add(form); split.add(listCard);
        content.add(split,BorderLayout.CENTER);
        root2.add(content,BorderLayout.CENTER); return root2;
    }

    // ════════════════════════════════════════════════
    // SCHEDULE
    // ════════════════════════════════════════════════
    static JPanel schedule(){
        JPanel root2=new JPanel(new BorderLayout());
        root2.setBackground(BG); root2.add(sidebar(SCHED),BorderLayout.WEST);
        JPanel content=new JPanel(new BorderLayout(0,18));
        content.setBackground(BG); content.setBorder(BorderFactory.createEmptyBorder(32,32,32,32));

        JPanel hdr=new JPanel(new BorderLayout()); hdr.setBackground(BG);
        hdr.add(pageHeader("Schedule","All your planned study sessions"),BorderLayout.WEST);
        JLabel statsL=mk(tStats(),12,Font.PLAIN,TEXT2); hdr.add(statsL,BorderLayout.EAST);
        content.add(hdr,BorderLayout.NORTH);

        if(tasks.isEmpty()){
            JPanel ep=new JPanel(new GridBagLayout()); ep.setBackground(BG);
            JPanel inner=new JPanel(); inner.setLayout(new BoxLayout(inner,BoxLayout.Y_AXIS));
            inner.setBackground(BG);
            JLabel m1=mk("No schedule yet",16,Font.BOLD,TEXT2); m1.setAlignmentX(0.5f);
            JLabel m2=mk("Add subjects to generate your schedule",13,Font.PLAIN,TEXT3); m2.setAlignmentX(0.5f);
            JButton go2=primaryBtn("Add Subjects"); go2.setMaximumSize(new Dimension(180,38)); go2.setAlignmentX(0.5f);
            go2.addActionListener(e->cards.show(root,ADD));
            inner.add(m1); inner.add(vsp(6)); inner.add(m2); inner.add(vsp(18)); inner.add(go2);
            ep.add(inner); content.add(ep,BorderLayout.CENTER);
            root2.add(content,BorderLayout.CENTER); return root2;
        }

        String[]cols={"","Subject","Date","Hours","Status"};
        DefaultTableModel model=new DefaultTableModel(cols,0){
            public Class<?>getColumnClass(int c){return c==0?Boolean.class:String.class;}
            public boolean isCellEditable(int r,int c){return c==0;}
        };
        for(StudyTask t:tasks)
            model.addRow(new Object[]{t.isDone(),t.getSubject().getName(),
                    t.getDate().toString(),t.getHours()+"h",sof(t)});

        JTable table=new JTable(model);
        table.setRowHeight(38); table.setBackground(ROW_ODD); table.setForeground(TEXT);
        table.setFont(new Font("Segoe UI",Font.PLAIN,13));
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0,1));
        table.setSelectionBackground(SEL_ROW); table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        JTableHeader th=table.getTableHeader();
        th.setBackground(SURFACE); th.setForeground(TEXT2);
        th.setFont(new Font("Segoe UI",Font.PLAIN,12));
        th.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER));
        int[]ws={40,200,120,70,100};
        for(int i=0;i<ws.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(ws[i]);
        table.getColumnModel().getColumn(0).setMaxWidth(46);

        // Status renderer
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){
                super.getTableCellRendererComponent(t,v,sel,foc,r,c);
                String s=(String)v;
                setForeground(s.equals("Done")?SUCCESS:s.equals("Overdue")?DANGER:s.equals("Today")?WARNING:TEXT3);
                setBackground(sel?SEL_ROW:(r%2==0?ROW_ODD:ROW_EVEN));
                setBorder(BorderFactory.createEmptyBorder(0,10,0,10)); return this;
            }
        });
        DefaultTableCellRenderer base=new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){
                super.getTableCellRendererComponent(t,v,sel,foc,r,c);
                setForeground(c==1?TEXT:TEXT2);
                setBackground(sel?SEL_ROW:(r%2==0?ROW_ODD:ROW_EVEN));
                setBorder(BorderFactory.createEmptyBorder(0,10,0,10)); return this;
            }
        };
        for(int i=1;i<=3;i++) table.getColumnModel().getColumn(i).setCellRenderer(base);

        model.addTableModelListener(e->{
            int row=e.getFirstRow(),col=e.getColumn();
            if(col==0&&row>=0&&row<tasks.size()){
                tasks.get(row).setDone((Boolean)model.getValueAt(row,0));
                javax.swing.SwingUtilities.invokeLater(()->{
                    // 1. Update status cell in the table — no flicker
                    model.setValueAt(sof(tasks.get(row)),row,4);
                    statsL.setText(tStats());
                    table.repaint();
                    // 2. Silently rebuild Dashboard and Progress in background
                    //    so when user navigates there, data is fresh
                    int total=root.getComponentCount();
                    // Remove and re-add DASH (index 1) and PROG (index 4)
                    // Order added in go(): LOGIN=0, DASH=1, ADD=2, SCHED=3, PROG=4
                    Component[] all=root.getComponents();
                    root.removeAll();
                    root.add(all[0], LOGIN);   // login — keep as is
                    root.add(dashboard(), DASH); // rebuild dashboard
                    root.add(all[2], ADD);     // add subject — keep
                    root.add(all[3], SCHED);   // schedule — keep current
                    root.add(progress(), PROG);  // rebuild progress
                    root.revalidate();
                    cards.show(root, SCHED);   // stay on schedule
                });
            }
        });

        JScrollPane sp=new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(BORDER,1));
        sp.getViewport().setBackground(ROW_ODD);
        content.add(sp,BorderLayout.CENTER);
        root2.add(content,BorderLayout.CENTER); return root2;
    }

    // ════════════════════════════════════════════════
    // PROGRESS
    // ════════════════════════════════════════════════
    static JPanel progress(){
        JPanel root2=new JPanel(new BorderLayout());
        root2.setBackground(BG); root2.add(sidebar(PROG),BorderLayout.WEST);
        JPanel content=new JPanel(new BorderLayout(0,20));
        content.setBackground(BG); content.setBorder(BorderFactory.createEmptyBorder(32,32,32,32));
        content.add(pageHeader("Progress","Track how far you've come"),BorderLayout.NORTH);

        if(tasks.isEmpty()){
            JLabel m=mk("Complete tasks to see your progress here",14,Font.PLAIN,TEXT2);
            m.setHorizontalAlignment(SwingConstants.CENTER);
            content.add(m,BorderLayout.CENTER); root2.add(content,BorderLayout.CENTER); return root2;
        }

        JPanel body=new JPanel(new BorderLayout(16,16)); body.setBackground(BG);

        double pct=tracker.overall(tasks);
        double studied=tracker.hoursStudied(tasks);
        double planned=tasks.stream().mapToDouble(StudyTask::getHours).sum();
        long overdue=tracker.overdue(tasks);
        long done=tasks.stream().filter(StudyTask::isDone).count();

        JPanel statsRow=new JPanel(new GridLayout(1,4,12,0));
        statsRow.setBackground(BG); statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,96));
        statsRow.add(tile("Completion",   String.format("%.0f%%",pct),    ACCENT));
        statsRow.add(tile("Studied",      String.format("%.1fh",studied), SUCCESS));
        statsRow.add(tile("Done",         done+"/"+tasks.size(),          new Color(167,139,250)));
        statsRow.add(tile("Overdue",      String.valueOf(overdue),        overdue>0?DANGER:SUCCESS));
        body.add(statsRow,BorderLayout.NORTH);

        JPanel bottom=new JPanel(new GridLayout(1,2,16,0)); bottom.setBackground(BG);

        // Subject progress bars
        JPanel barsCard=notionCard();
        barsCard.add(sectionTitle("By Subject")); barsCard.add(vsp(16));
        Map<String,Double> prog=tracker.perSubject(tasks);
        if(prog.isEmpty()) barsCard.add(emptyMsg("No data yet"));
        else prog.forEach((k,v)->{barsCard.add(progRow(k,v));barsCard.add(vsp(12));});
        barsCard.add(Box.createVerticalGlue());

        // Hours summary
        JPanel hoursCard=notionCard();
        hoursCard.add(sectionTitle("Hours Overview")); hoursCard.add(vsp(16));
        hoursCard.add(hoursLine("Studied",     String.format("%.1f h",studied),  SUCCESS));
        hoursCard.add(vsp(2)); hoursCard.add(hline()); hoursCard.add(vsp(2));
        hoursCard.add(hoursLine("Remaining",   String.format("%.1f h",planned-studied), WARNING));
        hoursCard.add(vsp(2)); hoursCard.add(hline()); hoursCard.add(vsp(2));
        hoursCard.add(hoursLine("Total Planned",String.format("%.1f h",planned),  TEXT2));
        hoursCard.add(vsp(20));

        // Completion ring
        double sf=studied, pf=planned;
        JPanel ring=new JPanel(){
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int cx=getWidth()/2,cy=getHeight()/2,r=55;
                g2.setStroke(new BasicStroke(12,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.setColor(BORDER); g2.drawOval(cx-r,cy-r,r*2,r*2);
                if(pf>0){
                    int angle=(int)(360.0*sf/pf);
                    g2.setColor(ACCENT);
                    g2.drawArc(cx-r,cy-r,r*2,r*2,90,-angle);
                }
                g2.setColor(TEXT); g2.setFont(new Font("Segoe UI",Font.BOLD,20));
                String pctS=String.format("%.0f%%",pf>0?sf/pf*100:0);
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(pctS,cx-fm.stringWidth(pctS)/2,cy+fm.getAscent()/2);
                g2.setColor(TEXT2); g2.setFont(new Font("Segoe UI",Font.PLAIN,11));
                String sub="of hours done"; fm=g2.getFontMetrics();
                g2.drawString(sub,cx-fm.stringWidth(sub)/2,cy+fm.getAscent()/2+18);
                g2.dispose();
            }
        };
        ring.setBackground(CARD);
        ring.setPreferredSize(new Dimension(0,150)); ring.setMaximumSize(new Dimension(Integer.MAX_VALUE,150));
        ring.setAlignmentX(0f);
        hoursCard.add(ring); hoursCard.add(Box.createVerticalGlue());

        bottom.add(barsCard); bottom.add(hoursCard);
        body.add(bottom,BorderLayout.CENTER);
        content.add(body,BorderLayout.CENTER);
        root2.add(content,BorderLayout.CENTER); return root2;
    }

    // ════════════════════════════════════════════════
    // SMALL COMPONENTS
    // ════════════════════════════════════════════════
    static JPanel tile(String label,String value,Color color){
        JPanel c=new JPanel();
        c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setBackground(CARD);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(16,18,16,18)));
        JLabel vl=mk(value,26,Font.BOLD,color); vl.setAlignmentX(0f);
        JLabel ll=mk(label,12,Font.PLAIN,TEXT2); ll.setAlignmentX(0f);
        c.add(vl); c.add(vsp(4)); c.add(ll);
        return c;
    }

    static JPanel notionCard(){
        JPanel p=new JPanel();
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(20,20,20,20)));
        return p;
    }

    static JPanel sessionRow(StudyTask t){
        JPanel row=new JPanel(new BorderLayout(8,0));
        row.setBackground(CARD_HVR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(9,12,9,12)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        // colored left strip
        JPanel strip=new JPanel();
        strip.setBackground(t.getSubject().getColor());
        strip.setPreferredSize(new Dimension(3,0));
        row.add(strip,BorderLayout.WEST);
        JLabel nm=mk(t.getSubject().getName(),13,Font.BOLD,TEXT);
        JLabel hr=mk(t.getHours()+"h",12,Font.PLAIN,TEXT2);
        JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0)); left.setBackground(CARD_HVR);
        left.add(nm); left.add(hr);
        row.add(left,BorderLayout.CENTER);
        row.add(mk(t.isDone()?"Done":"Pending",12,Font.PLAIN,t.isDone()?SUCCESS:WARNING),BorderLayout.EAST);
        return row;
    }

    static JPanel examRow(Subject s){
        long d=s.daysLeft();
        JPanel row=new JPanel(new BorderLayout(8,0));
        row.setBackground(CARD_HVR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(9,12,9,12)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        JPanel strip=new JPanel(); strip.setBackground(s.getColor()); strip.setPreferredSize(new Dimension(3,0));
        row.add(strip,BorderLayout.WEST);
        JPanel info=new JPanel(new GridLayout(2,1,0,2)); info.setBackground(CARD_HVR);
        info.add(mk(s.getName(),13,Font.BOLD,TEXT));
        info.add(mk(s.getExamDate().toString(),11,Font.PLAIN,TEXT2));
        row.add(info,BorderLayout.CENTER);
        Color uc=d<=7?DANGER:d<=14?WARNING:SUCCESS;
        row.add(mk(d+"d",12,Font.BOLD,uc),BorderLayout.EAST);
        return row;
    }

    static JPanel subjectChip(Subject s){
        JPanel c=new JPanel(new BorderLayout(0,4));
        c.setBackground(CARD_HVR);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(12,14,12,14)));
        // top colored bar
        JPanel bar=new JPanel(); bar.setBackground(s.getColor());
        bar.setPreferredSize(new Dimension(0,3)); bar.setMaximumSize(new Dimension(Integer.MAX_VALUE,3));
        c.add(bar,BorderLayout.NORTH);
        JPanel body=new JPanel(); body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS)); body.setBackground(CARD_HVR);
        body.add(mk(s.getName(),13,Font.BOLD,TEXT));
        body.add(vsp(3));
        body.add(mk(s.getDiffLabel(),12,Font.PLAIN,TEXT2));
        body.add(vsp(3));
        long d=s.daysLeft();
        body.add(mk(d+"d until exam",12,Font.PLAIN,d<=7?DANGER:d<=14?WARNING:TEXT3));
        c.add(body,BorderLayout.CENTER);
        return c;
    }

    static JPanel subjectListRow(Subject s){
        JPanel row=new JPanel(new BorderLayout(10,0));
        row.setBackground(CARD_HVR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER,1),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));
        JPanel strip=new JPanel(); strip.setBackground(s.getColor()); strip.setPreferredSize(new Dimension(3,0));
        row.add(strip,BorderLayout.WEST);
        JPanel info=new JPanel(new GridLayout(2,1,0,2)); info.setBackground(CARD_HVR);
        info.add(mk(s.getName(),13,Font.BOLD,TEXT));
        info.add(mk(s.getDiffLabel()+" · Exam: "+s.getExamDate(),11,Font.PLAIN,TEXT2));
        row.add(info,BorderLayout.CENTER);
        long d=s.daysLeft();
        row.add(mk(d+"d",12,Font.BOLD,d<=7?DANGER:d<=14?WARNING:SUCCESS),BorderLayout.EAST);
        return row;
    }

    static JPanel progRow(String name,double pct){
        JPanel row=new JPanel(new BorderLayout(10,0));
        row.setBackground(CARD); row.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        JLabel n=mk(name,13,Font.PLAIN,TEXT); n.setPreferredSize(new Dimension(130,20));
        row.add(n,BorderLayout.WEST);
        double pf=pct;
        JPanel bar=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int h=6,y=(getHeight()-h)/2;
                g2.setColor(BORDER); g2.fillRoundRect(0,y,getWidth(),h,h,h);
                int w=(int)(getWidth()*pf/100);
                if(w>0){g2.setColor(pf>=100?SUCCESS:ACCENT); g2.fillRoundRect(0,y,w,h,h,h);}
                g2.dispose();
            }
        };
        bar.setBackground(CARD);
        row.add(bar,BorderLayout.CENTER);
        JLabel p=mk(String.format("%.0f%%",pct),12,Font.PLAIN,pct>=100?SUCCESS:TEXT2);
        p.setPreferredSize(new Dimension(36,20)); p.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(p,BorderLayout.EAST); return row;
    }

    static JPanel hoursLine(String label,String value,Color c){
        JPanel row=new JPanel(new BorderLayout()); row.setBackground(CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        row.add(mk(label,13,Font.PLAIN,TEXT2),BorderLayout.WEST);
        row.add(mk(value,13,Font.BOLD,c),BorderLayout.EAST);
        return row;
    }

    // ── Helpers ──────────────────────────────────────
    static JPanel pageHeader(String title,String sub){
        JPanel p=new JPanel(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setBackground(BG);
        p.add(mk(title,22,Font.BOLD,TEXT)); p.add(vsp(3)); p.add(mkL(sub,13,TEXT2));
        return p;
    }
    static JLabel sectionTitle(String t){return mk(t,13,Font.BOLD,TEXT);}
    static JPanel emptyMsg(String msg){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,0,8)); p.setBackground(CARD);
        JLabel icon=mk("—",13,Font.PLAIN,BORDER2); p.add(icon);
        p.add(mk("  "+msg,12,Font.PLAIN,TEXT3)); return p;
    }
    static JPanel emptyMsgWithBtn(String msg, String btnLabel, String screen){
        JPanel p=new JPanel(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setBackground(CARD);
        p.setBorder(BorderFactory.createEmptyBorder(16,0,8,0));
        JLabel m=mk(msg,13,Font.PLAIN,TEXT2); m.setAlignmentX(0f);
        p.add(m); p.add(vsp(10));
        JButton b=new JButton(btnLabel);
        b.setFont(new Font("Segoe UI",Font.BOLD,12));
        b.setBackground(new Color(79,140,246,30)); b.setForeground(ACCENT);
        b.setBorder(BorderFactory.createLineBorder(new Color(79,140,246,80),1));
        b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(0f); b.setMaximumSize(new Dimension(160,30));
        b.addActionListener(e->cards.show(root,screen));
        p.add(b);
        return p;
    }
    static JPanel colorDot(Color c,int sz){
        JPanel d=new JPanel(){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.fillOval(0,0,sz,sz); g2.dispose();
            }
        };
        d.setPreferredSize(new Dimension(sz,sz)); d.setOpaque(false); return d;
    }
    static void placeholder(JTextField f, String hint){
        f.setForeground(TEXT3); f.setText(hint);
        f.addFocusListener(new FocusAdapter(){
            public void focusGained(FocusEvent e){
                if(f.getText().equals(hint)){f.setText(""); f.setForeground(TEXT);}
            }
            public void focusLost(FocusEvent e){
                if(f.getText().isEmpty()){f.setText(hint); f.setForeground(TEXT3);}
            }
        });
    }
    static JLabel mk(String t,int sz,int style,Color c){
        JLabel l=new JLabel(t); l.setFont(new Font("Segoe UI",style,sz)); l.setForeground(c); return l;
    }
    static JLabel mkL(String t,int sz,Color c){JLabel l=mk(t,sz,Font.PLAIN,c); l.setAlignmentX(0f); return l;}
    static JLabel mkL(String t,int sz,int style,Color c){JLabel l=mk(t,sz,style,c); l.setAlignmentX(0f); return l;}
    static Component vsp(int h){return Box.createVerticalStrut(h);}
    static JSeparator hline(){
        JSeparator s=new JSeparator(); s.setForeground(BORDER); s.setBackground(SURFACE);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE,1)); return s;
    }
    static JTextField tf(){JTextField f=new JTextField(); styleTf(f); return f;}
    static void styleTf(JTextField f){
        f.setFont(new Font("Segoe UI",Font.PLAIN,13));
        f.setBackground(SURFACE); f.setForeground(TEXT); f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER2,1),
                BorderFactory.createEmptyBorder(9,11,9,11)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE,38)); f.setAlignmentX(0f);
    }
    static JButton primaryBtn(String t){
        JButton b=new JButton(t);
        b.setFont(new Font("Segoe UI",Font.BOLD,13));
        b.setBackground(ACCENT); b.setForeground(Color.WHITE);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38)); b.setAlignmentX(0f);
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setBackground(ACCENT.brighter());}
            public void mouseExited(MouseEvent e) {b.setBackground(ACCENT);}
        });
        return b;
    }
    static JButton secondaryBtn(String t){
        JButton b=new JButton(t);
        b.setFont(new Font("Segoe UI",Font.PLAIN,13));
        b.setBackground(SURFACE); b.setForeground(TEXT2);
        b.setBorder(BorderFactory.createLineBorder(BORDER2,1));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38)); b.setAlignmentX(0f);
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setForeground(TEXT); b.setBackground(CARD_HVR);}
            public void mouseExited(MouseEvent e) {b.setForeground(TEXT2);b.setBackground(SURFACE);}
        });
        return b;
    }
    static JButton tinyBtn(String t){
        JButton b=new JButton(t);
        b.setFont(new Font("Segoe UI",Font.PLAIN,12));
        b.setBackground(TAG_BG); b.setForeground(ACCENT);
        b.setBorder(BorderFactory.createLineBorder(new Color(79,140,246,60),1));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static JButton flatBtn(String t,Color c){
        JButton b=new JButton(t);
        b.setFont(new Font("Segoe UI",Font.PLAIN,12)); b.setForeground(c);
        b.setBackground(SURFACE); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setForeground(DANGER);}
            public void mouseExited(MouseEvent e) {b.setForeground(c);}
        });
        return b;
    }
    static String sof(StudyTask t){
        if(t.isDone())return"Done"; if(t.isOverdue())return"Overdue"; if(t.isToday())return"Today"; return"Pending";
    }
    static String tStats(){
        long d=tasks.stream().filter(StudyTask::isDone).count();
        return d+"/"+tasks.size()+" done  ·  "+String.format("%.0f%%",tracker.overall(tasks))+" complete";
    }
}