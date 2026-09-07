(() => {
  const data = window.MODULENS_DATA;
  const findings = data.findings.findings;
  const modules = data.modules;
  const libraries = data.libraries.libraries;
  const state = { selectedModule: modules[0]?.path ?? null, moduleQuery: "", libraryQuery: "" };
  const byId = (id) => document.getElementById(id);
  const escape = (value) => String(value).replace(/[&<>'"]/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "'": "&#39;",
    "\"": "&quot;",
  })[character]);
  const moduleByPath = (path) => modules.find((module) => module.path === path);
  const relationshipList = (values) => values.length
    ? `<ul>${values.map((value) => `<li><code>${escape(value)}</code></li>`).join("")}</ul>`
    : '<p class="empty">None</p>';

  const summaryCards = [
    [data.project.modules, "Modules"],
    [data.project.dependencyEdges, "Dependency edges"],
    [data.project.maximumDependencyDepth, "Maximum depth"],
    [findings.length, "Findings"],
    [findings.filter((finding) => finding.severity === "ERROR").length, "Errors"],
    [data.libraries.versionConflicts.length, "Version conflicts"],
  ];

  byId("summary").innerHTML = summaryCards
    .map(([value, label], index) => `<article class="summary-card ${index === 3 ? "emphasis" : ""}"><span>${label}</span><strong>${value}</strong></article>`)
    .join("");

  const renderFindings = (filter) => {
    const query = filter.trim().toLowerCase();
    const visible = findings.filter((finding) =>
      `${finding.id} ${finding.subject} ${finding.message} ${finding.dependencyPath.join(" ")}`.toLowerCase().includes(query),
    );

    byId("findings-list").innerHTML = visible.length
      ? visible.map((finding) => `
          <article class="finding ${finding.severity.toLowerCase()}">
            <div class="finding-topline">
              <h3>${escape(finding.id.replaceAll("_", " ").toLowerCase())}</h3>
              <span class="badge">${escape(finding.severity)}</span>
            </div>
            <p><strong>${escape(finding.subject)}</strong></p>
            <p>${escape(finding.message)}</p>
            ${finding.dependencyPath.length ? `<p class="path">${finding.dependencyPath.map(escape).join(" → ")}</p>` : ""}
            ${finding.declarations.length ? `<p class="path">Declared by: ${finding.declarations.map(escape).join(", ")}</p>` : ""}
            ${finding.suggestedFix ? `<p><strong>Suggested fix:</strong> ${escape(finding.suggestedFix)}</p>` : ""}
          </article>`).join("")
      : '<p class="empty">No findings match this search.</p>';
  };

  const renderModuleOptions = () => {
    const query = state.moduleQuery.toLowerCase();
    const visible = modules.filter((module) => module.path.toLowerCase().includes(query));
    const select = byId("module-select");
    select.innerHTML = visible.map((module) => `<option value="${escape(module.path)}">${escape(module.path)}</option>`).join("");

    if (state.selectedModule && !visible.some((module) => module.path === state.selectedModule)) {
      state.selectedModule = visible[0]?.path ?? null;
    }
    select.value = state.selectedModule ?? "";
  };

  const renderModuleList = () => {
    const query = state.moduleQuery.toLowerCase();
    const visible = modules.filter((module) => module.path.toLowerCase().includes(query));
    byId("module-list").innerHTML = visible.length
      ? visible.map((module) => `
          <button class="module-row ${module.path === state.selectedModule ? "active" : ""}" type="button" data-module="${escape(module.path)}">
            <strong>${escape(module.path)}</strong>
            <span>${module.directDependencies.length} direct dependencies · ${module.libraries.length} resolved libraries</span>
          </button>`).join("")
      : '<p class="empty">No modules match this search.</p>';
  };

  const renderModuleDetail = () => {
    const module = moduleByPath(state.selectedModule);
    if (!module) {
      byId("module-details").innerHTML = '<p class="empty">Select a module to inspect it.</p>';
      return;
    }
    const metric = (value, label) => `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`;
    byId("module-details").innerHTML = `
      <h3><code>${escape(module.path)}</code></h3>
      <div class="metric-grid">
        ${metric(module.directDependencies.length, "Direct dependencies")}
        ${metric(module.transitiveDependencies.length, "Transitive dependencies")}
        ${metric(module.directDependents.length, "Direct dependents")}
        ${metric(module.affectedModules.length, "Affected modules")}
        ${metric(module.libraries.length, "Resolved libraries")}
        ${metric(`${module.projectCoverage.toFixed(1)}%`, "Project impact")}
      </div>
      <div class="relationship-grid">
        <div class="relationship"><h3>Direct dependencies</h3>${relationshipList(module.directDependencies)}</div>
        <div class="relationship"><h3>Direct dependents</h3>${relationshipList(module.directDependents)}</div>
        <div class="relationship"><h3>Transitive dependencies</h3>${relationshipList(module.transitiveDependencies)}</div>
        <div class="relationship"><h3>Affected modules</h3>${relationshipList(module.affectedModules)}</div>
      </div>
      <h3>Resolved libraries</h3>${relationshipList(module.libraries)}`;
  };

  const renderLibraries = () => {
    const query = state.libraryQuery.toLowerCase();
    const visible = libraries.filter((library) => {
      const matchesQuery = `${library.identifier} ${library.declaredVersions.join(" ")} ${library.resolvedVersion ?? ""} ${library.modules.join(" ")}`
        .toLowerCase()
        .includes(query);
      const matchesModule = !state.selectedModule || library.modules.includes(state.selectedModule);
      return matchesQuery && matchesModule;
    });

    byId("library-context").textContent = state.selectedModule
      ? `Showing libraries resolved for ${state.selectedModule}. Search a library to see every module where it appears.`
      : "Search a library to see every module where it appears.";
    byId("library-count").textContent = `${visible.length} of ${libraries.length} resolved libraries`;
    byId("library-list").innerHTML = visible.length
      ? visible.map((library) => `
          <tr>
            <td class="library-name ${library.declaredVersions.length > 1 ? "conflict" : ""}">${escape(library.identifier)}</td>
            <td>${library.declaredVersions.length ? escape(library.declaredVersions.join(", ")) : "Transitive only"}</td>
            <td>${escape(library.resolvedVersion ?? "Not resolved")}</td>
            <td>${library.modules.length
              ? library.modules.map((module) => `<button class="module-link" type="button" data-module="${escape(module)}">${escape(module)}</button>`).join("")
              : '<span class="hint">No selected-scope module source</span>'}</td>
          </tr>`).join("")
      : '<tr><td class="empty" colspan="4">No libraries match this filter.</td></tr>';
  };

  const renderModules = () => {
    renderModuleOptions();
    renderModuleList();
    renderModuleDetail();
  };

  const selectModule = (path) => {
    state.selectedModule = path;
    renderModules();
    renderLibraries();
  };

  byId("finding-filter").addEventListener("input", (event) => renderFindings(event.target.value));
  byId("module-filter").addEventListener("input", (event) => {
    state.moduleQuery = event.target.value;
    renderModules();
    renderLibraries();
  });
  byId("module-select").addEventListener("change", (event) => selectModule(event.target.value));
  byId("module-list").addEventListener("click", (event) => {
    const button = event.target.closest("[data-module]");
    if (button) selectModule(button.dataset.module);
  });
  byId("library-list").addEventListener("click", (event) => {
    const button = event.target.closest("[data-module]");
    if (button) selectModule(button.dataset.module);
  });
  byId("library-filter").addEventListener("input", (event) => {
    state.libraryQuery = event.target.value;
    renderLibraries();
  });
  byId("clear-module-filter").addEventListener("click", () => {
    state.selectedModule = null;
    byId("module-select").value = "";
    renderModuleList();
    renderModuleDetail();
    renderLibraries();
  });

  renderFindings("");
  renderModules();
  renderLibraries();
})();
