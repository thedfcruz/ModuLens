(() => {
  const data = window.MODULENS_DATA;
  const findings = data.findings.findings;
  const modules = [...data.modules].sort((a, b) => a.path.localeCompare(b.path));
  const libraries = [...data.libraries.libraries].sort((a, b) => a.identifier.localeCompare(b.identifier));
  const state = { tab: "findings", selectedModule: null, moduleQuery: "", libraryQuery: "", libraryModuleQuery: "", findingType: "ALL", findingSeverity: "ALL", selectedLibrary: null };
  const $ = (id) => document.getElementById(id);
  const escape = (value) => String(value).replace(/[&<>'"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", "\"": "&quot;" })[c]);
  const module = (path) => modules.find((item) => item.path === path);
  const library = (id) => libraries.find((item) => item.identifier === id);
  const empty = (message) => `<p class="empty">${escape(message)}</p>`;
  const moduleLink = (path) => `<button class="entity-link" type="button" data-module="${escape(path)}">${escape(path)}</button>`;
  const libraryLink = (id) => `<button class="entity-link" type="button" data-library="${escape(id)}">${escape(id)}</button>`;
  const label = (value) => value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
  const relatedModules = (finding) => finding.references?.modules?.length
    ? finding.references.modules
    : modules.filter((item) => [finding.subject, finding.message, ...finding.dependencyPath, ...finding.declarations].some((value) => value.includes(item.path))).map((item) => item.path);

  const summary = [[data.project.modules, "Modules"], [data.project.dependencyEdges, "Dependency edges"], [data.project.maximumDependencyDepth, "Maximum depth"], [findings.length, "Findings"], [findings.filter((item) => item.severity === "ERROR").length, "Errors"], [data.libraries.versionConflicts.length, "Version conflicts"]];
  $("summary").innerHTML = summary.map(([value, name], index) => `<article class="summary-card ${index === 3 ? "emphasis" : ""}"><span>${name}</span><strong>${value}</strong></article>`).join("");
  $("findings-tab-count").textContent = findings.length;
  $("modules-tab-count").textContent = modules.length;
  $("libraries-tab-count").textContent = libraries.length;

  const setTab = (tab) => {
    state.tab = tab;
    document.querySelectorAll("[data-tab]").forEach((element) => {
      const active = element.dataset.tab === tab;
      element.classList.toggle("active", active);
      if (element.matches(".tab")) element.setAttribute("aria-selected", String(active));
    });
    document.querySelectorAll(".tab-panel").forEach((panel) => {
      const active = panel.id === tab;
      panel.hidden = !active;
      panel.classList.toggle("active", active);
    });
    $(tab).scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const renderFindings = () => {
    const visible = findings.filter((item) => (state.findingType === "ALL" || item.id === state.findingType) && (state.findingSeverity === "ALL" || item.severity === state.findingSeverity));
    $("findings-list").innerHTML = visible.length ? visible.map((item) => {
      const affected = relatedModules(item);
      return `<article class="finding ${item.severity.toLowerCase()}"><div class="finding-topline"><div><span class="finding-type">${escape(label(item.id))}</span><h3>${escape(item.subject)}</h3></div><span class="badge">${escape(item.severity)}</span></div><p>${escape(item.message)}</p>${item.dependencyPath.length ? `<p class="path">${item.dependencyPath.map(escape).join(" → ")}</p>` : ""}${item.declarations.length ? `<p class="path">Declared by: ${item.declarations.map(escape).join(", ")}</p>` : ""}${affected.length ? `<div class="related-entities"><span>Affected modules</span>${affected.map(moduleLink).join("")}</div>` : ""}${item.suggestedFix ? `<p class="suggestion"><strong>Suggested fix:</strong> ${escape(item.suggestedFix)}</p>` : ""}</article>`;
    }).join("") : empty("No findings match these filters.");
  };

  const metricData = (item) => [[item.directDependencies.length, "Direct dependencies"], [item.transitiveDependencies.length, "Transitive dependencies"], [item.directDependents.length, "Direct dependents"], [item.affectedModules.length, "Affected modules"], [item.libraries.length, "Resolved libraries"], [`${item.projectCoverage.toFixed(1)}%`, "Project impact"]];
  const metrics = (item) => `<div class="metric-grid">${metricData(item).map(([value, name]) => `<div class="metric"><span>${name}</span><strong>${value}</strong></div>`).join("")}</div>`;
  const entityList = (items, type, noItems) => items.length ? `<div class="entity-list">${[...items].sort((left, right) => left.localeCompare(right)).map((item) => type === "module" ? moduleLink(item) : libraryLink(item)).join("")}</div>` : empty(noItems);
  const detail = (title, items, type, noItems) => `<section class="detail-section"><h3>${title}<span>${items.length}</span></h3>${entityList(items, type, noItems)}</section>`;
  const renderModules = () => {
    const selected = module(state.selectedModule);
    $("selected-module").hidden = !selected;
    $("selected-module").innerHTML = selected ? `<span>Selected: <code>${escape(selected.path)}</code></span><button class="icon-button" type="button" data-clear-module aria-label="Clear selected module">×</button>` : "";
    const visible = modules.filter((item) => item.path.toLowerCase().includes(state.moduleQuery.toLowerCase()));
    $("module-list").innerHTML = visible.length ? visible.map((item) => `<button class="module-row ${item.path === state.selectedModule ? "active" : ""}" type="button" data-module="${escape(item.path)}"><strong>${escape(item.path)}</strong><div class="module-preview"><span>${item.directDependencies.length} direct</span><span>${item.transitiveDependencies.length} transitive</span><span>${item.directDependents.length} dependents</span><span>${item.affectedModules.length} affected</span><span>${item.libraries.length} libraries</span><span>${item.projectCoverage.toFixed(1)}% impact</span></div></button>`).join("") : empty("No modules match this search.");
    if (!selected) { $("module-details").innerHTML = `<div class="details-placeholder"><h3>Select a module</h3><p>Choose any module to explore dependencies, impact, libraries, and findings.</p></div>`; return; }
    $("module-details").innerHTML = `<div class="details-title"><div><p class="eyebrow">SELECTED MODULE</p><h2><code>${escape(selected.path)}</code></h2></div></div>${metrics(selected)}<div class="detail-grid">${detail("Direct dependencies", selected.directDependencies, "module", "No direct module dependencies.")}${detail("Transitive dependencies", selected.transitiveDependencies, "module", "No transitive module dependencies.")}${detail("Direct dependents", selected.directDependents, "module", "No direct module dependents.")}${detail("Affected modules", selected.affectedModules, "module", "No transitively affected modules.")}</div>${detail("Resolved libraries", selected.libraries, "library", "No resolved external libraries.")}`;
  };

  const renderLibraries = () => {
    const visible = libraries.filter((item) => `${item.identifier} ${item.resolvedVersion ?? ""} ${item.declaredVersions.join(" ")}`.toLowerCase().includes(state.libraryQuery.toLowerCase()));
    $("library-count").textContent = `${visible.length} of ${libraries.length} resolved libraries`;
    $("clear-library-filter").hidden = !state.libraryQuery;
    $("library-list").innerHTML = visible.length ? visible.map((item) => {
      const transitive = item.modules.filter((path) => !item.directModules.includes(path)).length;
      const conflict = data.libraries.versionConflicts.includes(item.identifier);
      return `<article class="library-card ${conflict ? "has-conflict" : ""}"><div class="library-card-heading"><div><button class="library-title" type="button" data-library="${escape(item.identifier)}">${escape(item.identifier)}</button><p>Resolved ${escape(item.resolvedVersion ?? "version unavailable")}</p></div><span class="status ${conflict ? "conflict" : ""}">${conflict ? "Version conflict" : "Aligned"}</span></div><dl class="library-facts"><div><dt>Versions detected</dt><dd>${escape(item.declaredVersions.length ? item.declaredVersions.join(", ") : "Resolved transitively")}</dd></div><div><dt>Usage</dt><dd>${item.directModules.length ? `Directly used by ${item.directModules.length} module${item.directModules.length === 1 ? "" : "s"}` : "No direct declarations"}<br>${transitive ? `Transitively used by ${transitive} module${transitive === 1 ? "" : "s"}` : "No transitive usage"}</dd></div></dl><button class="usage-button" type="button" data-library-users="${escape(item.identifier)}">Used by ${item.modules.length} module${item.modules.length === 1 ? "" : "s"} <span>View modules →</span></button></article>`;
    }).join("") : empty("No libraries match this search.");
  };

  const renderLibraryDialog = () => {
    const selected = library(state.selectedLibrary);
    if (!selected) return;
    const visible = selected.modules.filter((path) => path.toLowerCase().includes(state.libraryModuleQuery.toLowerCase()));
    $("dialog-library-name").textContent = selected.identifier;
    $("dialog-module-list").innerHTML = visible.length ? visible.map((path) => `<button class="dialog-module" type="button" data-module="${escape(path)}"><code>${escape(path)}</code><span>${selected.directModules.includes(path) ? "Direct declaration" : "Transitive resolution"}</span></button>`).join("") : empty("No modules match this search.");
  };
  const selectModule = (path) => { state.selectedModule = path; state.moduleQuery = ""; $("module-filter").value = ""; if ($("library-modules-dialog").open) $("library-modules-dialog").close(); renderModules(); setTab("modules"); };
  const openLibrary = (id) => { state.selectedLibrary = id; setTab("libraries"); document.querySelector(`[data-library="${CSS.escape(id)}"]`)?.closest(".library-card")?.scrollIntoView({ behavior: "smooth", block: "center" }); };
  const openLibraryUsers = (id) => { state.selectedLibrary = id; state.libraryModuleQuery = ""; $("library-module-filter").value = ""; renderLibraryDialog(); $("library-modules-dialog").showModal(); };

  document.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-tab]"); if (tab) setTab(tab.dataset.tab);
    const moduleTarget = event.target.closest("[data-module]"); if (moduleTarget) selectModule(moduleTarget.dataset.module);
    const libraryTarget = event.target.closest("[data-library]"); if (libraryTarget) openLibrary(libraryTarget.dataset.library);
    const usersTarget = event.target.closest("[data-library-users]"); if (usersTarget) openLibraryUsers(usersTarget.dataset.libraryUsers);
    if (event.target.closest("[data-clear-module]")) { state.selectedModule = null; renderModules(); }
  });
  $("finding-type").addEventListener("change", (event) => { state.findingType = event.target.value; renderFindings(); });
  $("finding-severity").addEventListener("change", (event) => { state.findingSeverity = event.target.value; renderFindings(); });
  $("module-filter").addEventListener("input", (event) => { state.moduleQuery = event.target.value; state.selectedModule = null; renderModules(); });
  $("library-filter").addEventListener("input", (event) => { state.libraryQuery = event.target.value; renderLibraries(); });
  $("clear-library-filter").addEventListener("click", () => { state.libraryQuery = ""; $("library-filter").value = ""; renderLibraries(); });
  $("library-module-filter").addEventListener("input", (event) => { state.libraryModuleQuery = event.target.value; renderLibraryDialog(); });
  $("close-library-dialog").addEventListener("click", () => $("library-modules-dialog").close());
  $("library-modules-dialog").addEventListener("click", (event) => { if (event.target === $("library-modules-dialog")) $("library-modules-dialog").close(); });

  const types = [...new Set(findings.map((item) => item.id))].sort();
  const severities = [...new Set(findings.map((item) => item.severity))].sort();
  $("finding-type").innerHTML = `<option value="ALL">All finding types</option>${types.map((type) => `<option value="${escape(type)}">${escape(label(type))}</option>`).join("")}`;
  $("finding-severity").innerHTML = `<option value="ALL">All severities</option>${severities.map((severity) => `<option value="${escape(severity)}">${escape(label(severity))}</option>`).join("")}`;
  renderFindings(); renderModules(); renderLibraries();
})();
