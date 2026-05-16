export type InterviewExperience = {
  id: string;
  title: string;
  author: string;
  publishedAt: string;
  sourceUrl: string;
  tags: string[];
  highlights: string[];
  score: number;
};

export type InterviewSearchResponse = {
  query: string;
  total: number;
  items: InterviewExperience[];
};

export type LeetCodeProblem = {
  id: string;
  title: string;
  difficulty: "Easy" | "Medium" | "Hard";
  topics: string[];
  url: string;
};

export type PreparationTask = {
  day: number;
  theme: string;
  goal: string;
  actions: string[];
};

export type RiskNote = {
  level: "low" | "medium" | "high";
  title: string;
  evidence: string;
  nextAction: string;
};

export type PreparationPlan = {
  title: string;
  readinessScore: number;
  focusAreas: string[];
  dailyTasks: PreparationTask[];
  checklist: string[];
  risks: RiskNote[];
};

export type PrioritySignal = {
  level: "low" | "medium" | "high";
  title: string;
  evidence: string;
  action: string;
};

export type QuestionCluster = {
  topic: string;
  likelyQuestion: string;
  interviewerLens: string;
  drillSteps: string[];
};

export type StoryPrompt = {
  theme: string;
  prompt: string;
  proofPoints: string[];
};

export type InterviewBrief = {
  title: string;
  prioritySignals: PrioritySignal[];
  questionClusters: QuestionCluster[];
  storyBank: StoryPrompt[];
  followUpQuestions: string[];
};

export async function searchInterviewExperiences(input: {
  position: string;
  company: string;
  keywords: string[];
  page: number;
  size: number;
}): Promise<InterviewSearchResponse> {
  const response = await fetch("/api/interview-experiences/search", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });

  if (!response.ok) {
    throw new Error(`Search failed: ${response.status}`);
  }

  return response.json();
}

export async function fetchHotLeetCodeProblems(position: string, limit = 12): Promise<LeetCodeProblem[]> {
  const params = new URLSearchParams({ position, limit: String(limit) });
  const response = await fetch(`/api/leetcode/hot?${params.toString()}`);

  if (!response.ok) {
    throw new Error(`Problem list failed: ${response.status}`);
  }

  const data = await response.json();
  return data.items;
}

export async function generatePreparationPlan(input: {
  position: string;
  company: string;
  keywords: string[];
  days: number;
  interviews: InterviewExperience[];
  problems: LeetCodeProblem[];
}): Promise<PreparationPlan> {
  const response = await fetch("/api/preparation-plans/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });

  if (!response.ok) {
    throw new Error(`Plan generation failed: ${response.status}`);
  }

  return response.json();
}

export async function generateInterviewBrief(input: {
  position: string;
  company: string;
  keywords: string[];
  interviews: InterviewExperience[];
  problems: LeetCodeProblem[];
}): Promise<InterviewBrief> {
  const response = await fetch("/api/interview-briefs/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });

  if (!response.ok) {
    throw new Error(`Brief generation failed: ${response.status}`);
  }

  return response.json();
}
