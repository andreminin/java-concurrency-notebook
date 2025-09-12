# Java Concurrency Notebook
**Java Concurrency Notebook** is a personal training and educational project focused on exploring
asynchronous programming in Java. It demonstrates code samples with both legacy and modern APIs,
highlighting common pitfalls and best practices.
## Overview
This repository is structured around small, focused examples that illustrate various approaches to
asynchronous programming in Java:
- **Legacy APIs** - `Future` - Thread pools - Callables and Runnables
- **Modern APIs** - `CompletableFuture` - Structured concurrency (Project Loom) - Virtual threads
## Goals
- Provide a clear and practical reference for developers learning or revisiting Java concurrency. -
Compare old and new approaches side-by-side. - Showcase **bad practices** (anti-patterns) and their
improved **good practice** alternatives. - Build a reference notebook for personal and community use.
## Contents
- `futures/` – Examples using classic `Future` API. - `completablefuture/` – Examples of composition,
error handling, and pipelines. - `virtualthreads/` – Lightweight concurrency with Project Loom. -
`bad-vs-good/` – Direct comparisons of problematic vs. recommended code.
## How to Use
Clone the repository:
```bash git clone https://github.com/andreminin/java-concurrency-notebook.git cd
java-concurrency-notebook ```
Run examples with Maven or your preferred IDE. Each example is standalone and demonstrates a
specific concept.
## GitHub
Project URL: [https://github.com/andreminin/java-concurrency-notebook](https://github.com/andreminin
/java-concurrency-notebook)
---
> n This project is for **learning, experimentation, and reference** – not production code.
