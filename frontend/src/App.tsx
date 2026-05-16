import { CSSProperties, FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowUpRight,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Gauge,
  Loader2,
  MessageSquareText,
  Newspaper,
  Radar,
  Search,
  ServerCog,
  Sparkles,
  Target
} from "lucide-react";
import {
  InterviewBrief,
  InterviewExperience,
  LeetCodeProblem,
  PreparationPlan,
  TechRadar,
  fetchHotLeetCodeProblems,
  generateInterviewBrief,
  generatePreparationPlan,
  researchTechRadar,
  searchInterviewExperiences
} from "./api";

const defaultProblems: LeetCodeProblem[] = [
  {
    id: "leetcode-146",
    title: "LRU Cache",
    difficulty: "Medium",
    topics: ["Hash Table", "Linked List", "Design"],
    url: "https://leetcode.com/problems/lru-cache/"
  },
  {
    id: "leetcode-25",
    title: "Reverse Nodes in k-Group",
    difficulty: "Hard",
    topics: ["Linked List", "Recursion"],
    url: "https://leetcode.com/problems/reverse-nodes-in-k-group/"
  }
];

export function App() {
  const [position, setPosition] = useState("Java 后端开发");
  const [company, setCompany] = useState("字节跳动");
  const [keywordText, setKeywordText] = useState("Spring, Redis, 一面");
  const [days, setDays] = useState(5);
  const [items, setItems] = useState<InterviewExperience[]>([]);
  const [problems, setProblems] = useState<LeetCodeProblem[]>(defaultProblems);
  const [plan, setPlan] = useState<PreparationPlan | null>(null);
  const [brief, setBrief] = useState<InterviewBrief | null>(null);
  const [techRadar, setTechRadar] = useState<TechRadar | null>(null);
  const [activeTab, setActiveTab] = useState<"interviews" | "problems" | "radar" | "brief" | "plan">("interviews");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [radarLoading, setRadarLoading] = useState(false);
  const [briefing, setBriefing] = useState(false);
  const [planning, setPlanning] = useState(false);
  const [error, setError] = useState("");

  const keywords = useMemo(
    () =>
      keywordText
        .split(/[,，]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    [keywordText]
  );

  const planSourceCount = items.length + problems.length + (techRadar?.articles.length ?? 0);

  useEffect(() => {
    fetchHotLeetCodeProblems("java-backend", 12)
      .then(setProblems)
      .catch(() => {
        setProblems(defaultProblems);
      });
  }, []);

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const result = await searchInterviewExperiences({
        position,
        company,
        keywords,
        page: 1,
        size: 10
      });
      setItems(result.items);
      setQuery(result.query);
      setPlan(null);
      setBrief(null);
      setTechRadar(null);
      setActiveTab("interviews");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleResearchTech() {
    setRadarLoading(true);
    setError("");

    try {
      const result = await researchTechRadar({
        position,
        company,
        keywords,
        limit: 12
      });
      setTechRadar(result);
      setActiveTab("radar");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tech radar failed");
    } finally {
      setRadarLoading(false);
    }
  }

  async function handleGeneratePlan() {
    setPlanning(true);
    setError("");

    try {
      const result = await generatePreparationPlan({
        position,
        company,
        keywords,
        days,
        interviews: items,
        problems
      });
      setPlan(result);
      setActiveTab("plan");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Plan generation failed");
    } finally {
      setPlanning(false);
    }
  }

  async function handleGenerateBrief() {
    setBriefing(true);
    setError("");

    try {
      const result = await generateInterviewBrief({
        position,
        company,
        keywords,
        interviews: items,
        problems
      });
      setBrief(result);
      setActiveTab("brief");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Brief generation failed");
    } finally {
      setBriefing(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <p className="eyebrow">More Offer / M1 Offer Prep Cockpit</p>
          <h1>Turn interview signals and coding drills into an executable prep plan.</h1>
        </div>
        <div className="source-pill" title="Nowcoder and Web-Rooter run through MCP servers">
          <ServerCog size={18} />
          <span>Niuke + Web-Rooter MCP</span>
        </div>
      </section>

      <section className="workspace">
        <form className="search-panel" onSubmit={handleSearch}>
          <label>
            <span>Role</span>
            <input value={position} onChange={(event) => setPosition(event.target.value)} />
          </label>
          <label>
            <span>Company</span>
            <input value={company} onChange={(event) => setCompany(event.target.value)} />
          </label>
          <label className="keyword-field">
            <span>Keywords</span>
            <input value={keywordText} onChange={(event) => setKeywordText(event.target.value)} />
          </label>
          <label>
            <span>Days</span>
            <input
              min={1}
              max={14}
              type="number"
              value={days}
              onChange={(event) => setDays(Number(event.target.value))}
            />
          </label>
          <button type="submit" disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} /> : <Search size={18} />}
            <span>Search</span>
          </button>
        </form>

        {error && <p className="error-text">{error}</p>}

        <section className="insight-strip">
          <MetricCard icon={<BriefcaseBusiness size={18} />} label="Interviews" value={items.length} />
          <MetricCard icon={<ClipboardList size={18} />} label="Problems" value={problems.length} />
          <MetricCard icon={<Radar size={18} />} label="CN Tech Sources" value={techRadar?.articles.length ?? "--"} />
          <MetricCard icon={<Gauge size={18} />} label="Readiness" value={plan?.readinessScore ?? "--"} />
        </section>

        <section className="content-grid">
          <div className="results-area">
            <div className="tabs" role="tablist" aria-label="Content view">
              <button type="button" className={activeTab === "interviews" ? "active" : ""} onClick={() => setActiveTab("interviews")}>
                <BriefcaseBusiness size={17} />
                <span>Interviews</span>
              </button>
              <button type="button" className={activeTab === "problems" ? "active" : ""} onClick={() => setActiveTab("problems")}>
                <ClipboardList size={17} />
                <span>Problems</span>
              </button>
              <button type="button" className={activeTab === "radar" ? "active" : ""} onClick={() => setActiveTab("radar")}>
                <Radar size={17} />
                <span>Radar</span>
              </button>
              <button type="button" className={activeTab === "brief" ? "active" : ""} onClick={() => setActiveTab("brief")}>
                <MessageSquareText size={17} />
                <span>Brief</span>
              </button>
              <button type="button" className={activeTab === "plan" ? "active" : ""} onClick={() => setActiveTab("plan")}>
                <CalendarDays size={17} />
                <span>Plan</span>
              </button>
            </div>

            {activeTab === "interviews" && <InterviewResults items={items} query={query} />}
            {activeTab === "problems" && <ProblemList items={problems} />}
            {activeTab === "radar" && <TechRadarView radar={techRadar} onResearch={handleResearchTech} loading={radarLoading} />}
            {activeTab === "brief" && <BriefView brief={brief} onGenerate={handleGenerateBrief} briefing={briefing} />}
            {activeTab === "plan" && <PlanView plan={plan} onGenerate={handleGeneratePlan} planning={planning} />}
          </div>

          <aside className="summary-panel">
            <div className="panel-heading">
              <p className="panel-title">Prep Command</p>
              <Sparkles size={18} />
            </div>
            <button className="plan-button" type="button" onClick={handleGeneratePlan} disabled={planning}>
              {planning ? <Loader2 className="spin" size={18} /> : <Sparkles size={18} />}
              <span>Generate Plan</span>
            </button>
            <button className="secondary-button" type="button" onClick={handleGenerateBrief} disabled={briefing}>
              {briefing ? <Loader2 className="spin" size={18} /> : <MessageSquareText size={18} />}
              <span>Build Brief</span>
            </button>
            <button className="secondary-button" type="button" onClick={handleResearchTech} disabled={radarLoading}>
              {radarLoading ? <Loader2 className="spin" size={18} /> : <Radar size={18} />}
              <span>Research Tech</span>
            </button>
            <p className="hint-text">The plan will use {planSourceCount} current signals.</p>

            <div className="chips">
              {keywords.map((keyword) => (
                <span key={keyword}>{keyword}</span>
              ))}
            </div>

            {plan && (
              <>
                <div className="score-ring" style={{ "--score": plan.readinessScore } as CSSProperties}>
                  <strong>{plan.readinessScore}</strong>
                  <span>score</span>
                </div>
                <div className="focus-list">
                  {plan.focusAreas.map((area) => (
                    <span key={area}>{area}</span>
                  ))}
                </div>
              </>
            )}

            {techRadar && (
              <div className="radar-mini">
                <span>{techRadar.generatedByOpenAi ? "OpenAI summary" : "Local summary"}</span>
                <strong>{techRadar.themes[0] ?? "Tech radar"}</strong>
                <p>{techRadar.articles.length} Chinese-first sources</p>
              </div>
            )}
          </aside>
        </section>
      </section>
    </main>
  );
}

function MetricCard({ icon, label, value }: { icon: ReactNode; label: string; value: string | number }) {
  return (
    <article className="metric-card">
      <div>{icon}</div>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function InterviewResults({ items, query }: { items: InterviewExperience[]; query: string }) {
  if (items.length === 0) {
    return (
      <div className="empty-state">
        <Search size={28} />
        <p>
          {query
            ? `No Nowcoder results returned for: ${query}`
            : "Search by role, company, and keywords to fetch Nowcoder interview summaries."}
        </p>
      </div>
    );
  }

  return (
    <div className="list-stack">
      {query && <p className="query-line">Current search: {query}</p>}
      {items.map((item) => (
        <article className="result-card" key={item.id}>
          <div className="result-heading">
            <div>
              <h2>{item.title}</h2>
              <p>
                {item.author || "Anonymous"} - {item.publishedAt || "Unknown time"} - Match {Math.round(item.score * 100)}%
              </p>
            </div>
            <a href={item.sourceUrl} target="_blank" rel="noreferrer" aria-label="Open source">
              <ArrowUpRight size={18} />
            </a>
          </div>
          <div className="tags">
            {item.tags.map((tag) => (
              <span key={tag}>{tag}</span>
            ))}
          </div>
          <ul className="highlights">
            {item.highlights.map((highlight) => (
              <li key={highlight}>{highlight}</li>
            ))}
          </ul>
        </article>
      ))}
    </div>
  );
}

function ProblemList({ items }: { items: LeetCodeProblem[] }) {
  return (
    <div className="list-stack">
      {items.map((problem) => (
        <article className="problem-row" key={problem.id}>
          <div>
            <h2>{problem.title}</h2>
            <p>{problem.topics.join(" / ")}</p>
          </div>
          <a href={problem.url} target="_blank" rel="noreferrer" className="problem-link">
            <span className={`difficulty ${problem.difficulty.toLowerCase()}`}>{problem.difficulty}</span>
            <ArrowUpRight size={16} />
          </a>
        </article>
      ))}
    </div>
  );
}

function TechRadarView({
  radar,
  onResearch,
  loading
}: {
  radar: TechRadar | null;
  onResearch: () => void;
  loading: boolean;
}) {
  if (!radar) {
    return (
      <div className="empty-state">
        <Radar size={28} />
        <p>Run Web-Rooter research for the current interview context.</p>
        <button className="inline-action" type="button" onClick={onResearch} disabled={loading}>
          {loading ? <Loader2 className="spin" size={18} /> : <Radar size={18} />}
          <span>Research Tech</span>
        </button>
      </div>
    );
  }

  return (
    <div className="radar-layout">
      <section className="radar-header">
        <div>
          <p className="eyebrow">Tech Radar</p>
          <h2>{radar.query}</h2>
        </div>
        <span>{radar.generatedByOpenAi ? "OpenAI" : "Local"}</span>
      </section>

      <section className="radar-summary">
        <Newspaper size={19} />
        <p>{radar.summary}</p>
      </section>

      <section className="radar-columns">
        <div className="checklist">
          <h3>Themes</h3>
          {radar.themes.map((theme) => (
            <p key={theme}>
              <Target size={17} />
              <span>{theme}</span>
            </p>
          ))}
        </div>
        <div className="checklist">
          <h3>Interview Signals</h3>
          {radar.interviewSignals.map((signal) => (
            <p key={signal}>
              <MessageSquareText size={17} />
              <span>{signal}</span>
            </p>
          ))}
        </div>
      </section>

      <div className="list-stack">
        {radar.articles.map((article) => (
          <article className="result-card" key={article.id}>
            <div className="result-heading">
              <div>
                <h2>{article.title}</h2>
                <p>
                  {article.source} - {article.publishedAt || "recent"} - Match {Math.round(article.score * 100)}%
                </p>
              </div>
              <a href={article.sourceUrl} target="_blank" rel="noreferrer" aria-label="Open article">
                <ArrowUpRight size={18} />
              </a>
            </div>
            <p className="article-snippet">{article.snippet || "Open the source for the full technical discussion."}</p>
          </article>
        ))}
      </div>
    </div>
  );
}

function BriefView({
  brief,
  onGenerate,
  briefing
}: {
  brief: InterviewBrief | null;
  onGenerate: () => void;
  briefing: boolean;
}) {
  if (!brief) {
    return (
      <div className="empty-state">
        <MessageSquareText size={28} />
        <p>Build a focused interview brief from the current search context.</p>
        <button className="inline-action" type="button" onClick={onGenerate} disabled={briefing}>
          {briefing ? <Loader2 className="spin" size={18} /> : <MessageSquareText size={18} />}
          <span>Build Brief</span>
        </button>
      </div>
    );
  }

  return (
    <div className="brief-layout">
      <section className="brief-header">
        <p className="eyebrow">Interview Brief</p>
        <h2>{brief.title}</h2>
      </section>

      <section className="signal-grid">
        {brief.prioritySignals.map((signal) => (
          <article className={`risk-card ${signal.level}`} key={signal.title}>
            <Target size={18} />
            <div>
              <h3>{signal.title}</h3>
              <p>{signal.evidence}</p>
              <span>{signal.action}</span>
            </div>
          </article>
        ))}
      </section>

      <section className="cluster-list">
        {brief.questionClusters.map((cluster) => (
          <article className="cluster-card" key={cluster.topic}>
            <div>
              <span>{cluster.topic}</span>
              <h3>{cluster.likelyQuestion}</h3>
              <p>{cluster.interviewerLens}</p>
            </div>
            <ul>
              {cluster.drillSteps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ul>
          </article>
        ))}
      </section>

      <section className="brief-columns">
        <div className="checklist">
          <h3>Story Bank</h3>
          {brief.storyBank.map((story) => (
            <p key={story.theme}>
              <CheckCircle2 size={17} />
              <span>{story.theme}: {story.prompt} Proof: {story.proofPoints.join(", ")}</span>
            </p>
          ))}
        </div>
        <div className="checklist">
          <h3>Follow-ups</h3>
          {brief.followUpQuestions.map((question) => (
            <p key={question}>
              <MessageSquareText size={17} />
              <span>{question}</span>
            </p>
          ))}
        </div>
      </section>
    </div>
  );
}

function PlanView({
  plan,
  onGenerate,
  planning
}: {
  plan: PreparationPlan | null;
  onGenerate: () => void;
  planning: boolean;
}) {
  if (!plan) {
    return (
      <div className="empty-state">
        <CalendarDays size={28} />
        <p>Generate a sprint plan from the current interviews and problem list.</p>
        <button className="inline-action" type="button" onClick={onGenerate} disabled={planning}>
          {planning ? <Loader2 className="spin" size={18} /> : <Sparkles size={18} />}
          <span>Generate Plan</span>
        </button>
      </div>
    );
  }

  return (
    <div className="plan-layout">
      <section className="plan-header">
        <div>
          <p className="eyebrow">Sprint Plan</p>
          <h2>{plan.title}</h2>
        </div>
        <strong>{plan.readinessScore}</strong>
      </section>

      <section className="risk-grid">
        {plan.risks.map((risk) => (
          <article className={`risk-card ${risk.level}`} key={risk.title}>
            <AlertTriangle size={18} />
            <div>
              <h3>{risk.title}</h3>
              <p>{risk.evidence}</p>
              <span>{risk.nextAction}</span>
            </div>
          </article>
        ))}
      </section>

      <section className="task-list">
        {plan.dailyTasks.map((task) => (
          <article className="task-card" key={task.day}>
            <div className="task-day">Day {task.day}</div>
            <div>
              <h3>{task.theme}</h3>
              <p>{task.goal}</p>
              <ul>
                {task.actions.map((action) => (
                  <li key={action}>{action}</li>
                ))}
              </ul>
            </div>
          </article>
        ))}
      </section>

      <section className="checklist">
        <h3>Before Interview</h3>
        {plan.checklist.map((item) => (
          <p key={item}>
            <CheckCircle2 size={17} />
            <span>{item}</span>
          </p>
        ))}
      </section>
    </div>
  );
}
