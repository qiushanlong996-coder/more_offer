# PM Requirements

## Product Manager

Name: Lin Zhixia

Role: Product manager for More Offer's interview-preparation workspace.

Working style: Lin cares less about collecting more links and more about helping a candidate decide what to do tonight. Her product demand for M1 is: every search should produce a short, executable preparation plan.

## Milestone M1: Offer Prep Cockpit

### User Story

As a software engineering candidate, I want to search interview experiences for a target company and role, then immediately turn the findings into a focused preparation plan, so I can spend my limited study time on the highest-risk topics.

### Scope

- Keep the existing Nowcoder MCP search path: frontend -> Spring Boot backend -> dedicated MCP server.
- Add a backend preparation-plan API that accepts the search context, selected interview summaries, and hot problems, then returns a deterministic study plan.
- Redesign the first screen into a usable cockpit with search inputs, source status, result cards, hot problems, readiness score, focus areas, risks, and daily tasks.
- Avoid account systems, persistent storage, Redis, and MySQL in M1. The product value is in single-session decision support, and persistence can wait until users can save plans.

### Acceptance Criteria

- A candidate can search by role, company, and keywords.
- Search results still include source links and structured highlights.
- The UI can generate a preparation plan from current search results and hot problems.
- The plan includes readiness score, focus areas, day-by-day tasks, a checklist, and risk notes.
- The frontend stays responsive on mobile and desktop.
- The backend builds successfully.
- Deployment instructions and scripts exist for the CentOS server release.

### Out Of Scope

- User login and saved plans.
- Paid model API calls.
- Direct frontend scraping of third-party sites.
- Long-term storage of Nowcoder full text.

## M2 Requirement - Interview Brief

Lin Zhixia's follow-up requirement: after a candidate searches interview notes and sees the prep plan, the product must also provide a concise interview brief. The brief should answer four questions without requiring another tool:

- What signals are most likely to matter in this interview?
- Which question clusters should the candidate rehearse first?
- Which personal stories should be prepared for behavioral and trade-off questions?
- What follow-up questions should the candidate ask the interviewer?

Acceptance criteria:

- Backend exposes `POST /api/interview-briefs/generate`.
- The response includes priority signals, question clusters, story prompts, and follow-up questions.
- Frontend adds a Brief tab and a Build Brief action.
- The feature remains stateless and deployable with the existing M1 runtime.
