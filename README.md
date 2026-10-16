# FlowPilot AI 🚀
### AI-Powered Project Management & Decision-Support Platform

FlowPilot AI is an AI-powered enterprise project management platform designed to help teams organize projects, coordinate tasks, monitor progress, and make better decisions through intelligent, evidence-based insights.

Combining a Java and Spring Boot backend with a modern web interface and LLM-powered AI capabilities, FlowPilot AI aims to go beyond traditional task management by helping teams identify potential risks, understand project status, and turn project data into actionable recommendations.

> **Project status:** In development  
> **Project type:** Fifth-year Software Engineering academic project (PFA)

---

## 🎯 Project Vision

Traditional project management tools help teams track tasks and deadlines, but understanding why a project is falling behind or what should be prioritized next often requires manual analysis.

FlowPilot AI aims to address this challenge through an intelligent project analyst that uses authorized project data and project documentation to provide contextual answers, identify potential risks, and support decision-making.

### Key Objectives

- Simplify project and task management.
- Improve visibility into project progress and potential delivery risks.
- Integrate AI into real business workflows.
- Provide evidence-based recommendations grounded in project data.
- Apply secure, maintainable, and scalable software engineering practices.

## ✨ Planned Features

### 📊 Project & Task Management
- Project creation and team collaboration.
- Task assignment, priorities, statuses, and deadlines.
- Task dependencies and blocked-task tracking.
- Milestone management and progress monitoring.
- Project activity history and comments.

### 🤖 AI Project Analyst
- Ask natural-language questions about project status.
- Generate project progress summaries.
- Identify potential risks and delivery bottlenecks.
- Analyze overdue tasks and dependencies.
- Recommend priorities and possible corrective actions.
- Provide supporting evidence from project records.

### 📚 AI Knowledge Hub
- Upload and process project documentation.
- Ask questions about project requirements and reports.
- Retrieve relevant document passages using semantic search.
- Generate context-aware answers with source references.

### 📈 Analytics & Reporting
- Project progress dashboards.
- Overdue and blocked-task indicators.
- Milestone and workload summaries.
- AI-assisted weekly reports.
- Transparent project health indicators based on application data.

### 🔐 Security & Access Control
- Secure authentication and authorization.
- Organization-based data isolation.
- Role-based permissions.
- Protected project and document access.
- Audit trails for sensitive operations.

### ⚙️ Infrastructure & Traffic Management
- Nginx reverse proxy and load balancing.
- Multiple Spring Boot application replicas.
- Docker-based development and deployment.
- Health checks and overload protection.
- Automated testing and CI/CD.

*Features will be implemented incrementally throughout development.*

---

## 🛠️ Technology Stack

| Component | Technologies |
|---|---|
| Frontend | React, TypeScript, Vite |
| Backend | Java, Spring Boot |
| Persistence | Spring Data JPA, Hibernate |
| Security | Spring Security |
| Database | PostgreSQL |
| AI Integration | OLLAMA |
| Semantic Search | pgvector, embeddings, RAG |
| API Documentation | OpenAPI / Swagger UI |
| Reverse Proxy & Load Balancing | Nginx |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, Testcontainers |
| CI/CD | GitHub Actions |
| Monitoring | Spring Boot Actuator, optional Prometheus and Grafana |

The backend follows a **modular monolith architecture**. Spring Cloud and microservices are intentionally excluded from the initial design to keep the application maintainable while allowing multiple application instances to run behind Nginx.

## 🏗️ Architecture Overview

```text
                  React + TypeScript
                          |
                          v
                  Nginx Reverse Proxy
                  & Load Balancer
                          |
              +-----------+-----------+
              |                       |
              v                       v
       Spring Boot #1          Spring Boot #2
              |                       |
              +-----------+-----------+
                          |
              +-----------+-----------+
              |                       |
              v                       v
          PostgreSQL                OLLAMA
```

The application backend is organized into modules responsible for identity, organizations, projects, tasks, collaboration, documents, AI, analytics, notifications, and auditing.

Nginx distributes incoming requests across application instances. PostgreSQL stores persistent application data and vector embeddings, while OLLAMA AI connects the backend to the configured language model provider.

AI operations are designed to respect application permissions, use controlled backend tools, and distinguish factual project data from generated recommendations.

## 🧠 AI Integration

FlowPilot AI is designed around two complementary AI capabilities.

**1. Tool-based project analysis**

The AI assistant can request information through controlled backend functions, such as retrieving project summaries, overdue tasks, blocked dependencies, and milestone statuses. The backend validates permissions and executes these operations.

**2. Retrieval-Augmented Generation (RAG)**

Project documents are processed into text chunks and embeddings. Relevant passages are retrieved when a user asks a question, allowing the AI to produce answers grounded in available documentation.

The project aims to evaluate AI responses for factual correctness, citation quality, retrieval relevance, latency, and cost.

## 🛡️ Engineering Principles

- **Security first:** authorization is enforced by the backend, including AI tool calls.
- **Modular design:** clear boundaries between business domains.
- **Data integrity:** PostgreSQL and database migrations support reliable persistence.
- **Responsible AI:** generated recommendations are distinguished from verified facts.
- **Scalability:** stateless application replicas and controlled traffic distribution.
- **Quality assurance:** automated tests and continuous integration.
- **Observability:** application health, latency, errors, and resource usage are monitored.
- **Maintainability:** readable code, documented APIs, and consistent development practices.

## 🚧 Development Roadmap

- [X] Requirements analysis and system architecture
- [ ] Database schema and project structure
- [ ] Authentication and organization management
- [ ] Project and task management
- [ ] Dashboard and analytics
- [ ] LLM integration and AI Project Analyst
- [ ] Document processing and RAG
- [ ] Nginx reverse proxy and load balancing
- [ ] Docker Compose deployment
- [ ] Automated testing and CI/CD
- [ ] AI evaluation and load testing
- [ ] Documentation and final demonstration

## 🚀 Getting Started

The application is under development, and the complete deployment procedure will be documented as the implementation progresses.

### Prerequisites

The planned development environment includes:

- JDK 21
- Node.js and npm
- Docker and Docker Compose
- PostgreSQL, if running the database outside Docker
- An API key for the selected LLM provider

### Planned Setup

Once the initial implementation is available:

```bash
# Clone the repository
git clone <repository-url>

# Enter the project directory
cd FlowPilot-AI

# Start infrastructure services
docker compose up -d

# Run the backend using the project's documented Maven command
# Install frontend dependencies and start the development server
```

The exact commands, required environment variables, and service configuration will be added once the project structure and deployment setup are finalized.

Never commit API keys, passwords, or other secrets to the repository.

## 🧪 Testing & Evaluation

The project will include automated tests for business logic, API behavior, authentication, authorization, and tenant isolation.

AI-specific evaluation will focus on:
- Accuracy of project-related answers.
- Correctness of supporting references.
- Relevance of retrieved document passages.
- Handling of missing or conflicting information.
- Response latency and approximate API cost.
- Prevention of unauthorized data access.

Load testing will assess traffic distribution, application-instance failures, and behavior under high request volume.

## 👥 Team & Academic Context

FlowPilot AI is being developed as a fifth-year Software Engineering academic project, with an emphasis on enterprise application architecture, AI integration, backend engineering, and DevOps practices.

Team members, contributions, and acknowledgements will be documented as development progresses.

## 📌 Project Scope

FlowPilot AI is an academic engineering project intended to demonstrate practical software development and applied AI techniques. It is not currently presented as a production-certified enterprise service.

Security, performance, scalability, and AI quality claims will be supported by implementation details and test results as they become available.

---

**FlowPilot AI — From project tracking to intelligent project decisions.**