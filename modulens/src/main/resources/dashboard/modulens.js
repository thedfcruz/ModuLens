(() => {
  const data = window.MODULENS_DATA;
  const byId = (id) => document.getElementById(id);
  const escape = (value) => String(value).replace(/[&<>'"]/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "'": "&#39;",
    "\"": "&quot;",
  })[character]);
  const list = (values) => values && values.length
    ? `<ul>${values.map((value) => `<li><code>${escape(value)}</code></li>`).join("")}</ul>`
    : '<p class="hint">None</p>';

  const findings = data.findings.findings;
  const cards = [
    [data.project.modules, "Modules"],
    [data.project.dependencyEdges, "Dependency edges"],
    [data.project.maximumDependencyDepth, "Maximum depth"],
    [findings.length, "Findings"],
    [findings.filter((item) => item.severity === "ERROR").length, "Errors"],
    [data.libraries.versionConflicts.length, "Version conflicts"],
  ];

  byId("summary").innerHTML = cards
    .map(([value, label]) => `<article class="card"><span>${label}</span><strong>${value}</strong></article>`)
    .join("");

  const renderFindings = (filter) => {
    const query = filter.toLowerCase();
    const visible = findings.filter((item) =>
      `${item.id} ${item.subject} ${item.message}`.toLowerCase().includes(query),
    );

    byId("findings").innerHTML = visible.length
      ? visible.map((item) => `
          <article class="finding ${item.severity.toLowerCase()}">
            <h3>${escape(item.severity)} · ${escape(item.id.replaceAll("_", " "))}</h3>
            <p><strong>${escape(item.subject)}</strong></p>
            <p>${escape(item.message)}</p>
            ${item.dependencyPath.length ? `<p class="path">${item.dependencyPath.map(escape).join(" → ")}</p>` : ""}
            ${item.declarations.length ? `<p class="path">Declared by: ${item.declarations.map(escape).join(", ")}</p>` : ""}
            ${item.suggestedFix ? `<p><strong>Suggested fix:</strong> ${escape(item.suggestedFix)}</p>` : ""}
          </article>`).join("")
      : '<p class="hint">No findings match this filter.</p>';
  };

  byId("finding-filter").addEventListener("input", (event) => renderFindings(event.target.value));
  renderFindings("");

  const select = byId("module-select");
  select.innerHTML = data.modules
    .map((module) => `<option value="${escape(module.path)}">${escape(module.path)}</option>`)
    .join("");

  const renderModule = (path) => {
    const module = data.modules.find((item) => item.path === path);
    const metric = (value, label) => `<div><span>${label}</span><strong>${value}</strong></div>`;

    byId("module-details").innerHTML = `
      <div class="metric-list">
        ${metric(module.directDependencies.length, "Direct dependencies")}
        ${metric(module.transitiveDependencies.length, "Transitive dependencies")}
        ${metric(module.directDependents.length, "Direct dependents")}
        ${metric(module.affectedModules.length, "Affected modules")}
        ${metric(module.dependencyDepth, "Dependency depth")}
        ${metric(`${module.projectCoverage.toFixed(1)}%`, "Project impact")}
      </div>
      <h3>Direct dependencies</h3>${list(module.directDependencies)}
      <h3>Transitive dependencies</h3>${list(module.transitiveDependencies)}
      <h3>Direct dependents</h3>${list(module.directDependents)}
      <h3>Affected modules</h3>${list(module.affectedModules)}`;
  };

  select.addEventListener("change", (event) => renderModule(event.target.value));
  renderModule(select.value);

  byId("libraries").innerHTML = data.libraries.libraries.length
    ? `<table><thead><tr><th>Library</th><th>Declared</th><th>Resolved</th><th>Modules</th></tr></thead><tbody>${data.libraries.libraries
        .map((library) => `
          <tr>
            <td class="${library.declaredVersions.length > 1 ? "conflict" : ""}">${escape(library.identifier)}</td>
            <td>${escape(library.declaredVersions.join(", "))}</td>
            <td>${escape(library.resolvedVersion || "Not resolved")}</td>
            <td>${library.modules.map(escape).join("<br>")}</td>
          </tr>`).join("")}</tbody></table>`
    : '<p class="hint">No external libraries were declared in the selected scopes.</p>';
})();
