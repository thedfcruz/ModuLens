(() => {
  const data = window.MODULENS_DATA;
  const findings = data.findings.findings;
  const modules = [...data.modules].sort((a, b) => a.path.localeCompare(b.path));
  const libraries = [...data.libraries.libraries].sort((a, b) => a.identifier.localeCompare(b.identifier));
  const directLibraryCounts = data.directLibraryDeclarations.reduce((counts, declaration) => {
    counts[declaration.module] = (counts[declaration.module] ?? 0) + 1;
    return counts;
  }, {});
  const state = { tab: "findings", selectedModule: null, moduleQuery: "", moduleSort: "NAME", moduleSortDirection: "ASC", libraryQuery: "", librarySource: "ALL", libraryConflict: "ALL", librarySort: "NAME", librarySortDirection: "ASC", findingType: "ALL", findingSeverity: "ALL", selectedLibrary: null };
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
  const directLibraryDeclarations = data.directLibraryDeclarations ?? [];
  const libraryDeclarations = (id) => directLibraryDeclarations
    .filter((item) => item.identifier === id)
    .sort((left, right) => left.module.localeCompare(right.module) || left.configuration.localeCompare(right.configuration));
  const directConsumers = (id) => [...new Set(libraryDeclarations(id).map((item) => item.module))].sort();
  const reverseDependencies = modules.reduce((result, item) => {
    item.directDependencies.forEach((dependency) => (result[dependency] ??= []).push(item.path));
    return result;
  }, {});
  const consumersOfModules = (paths) => {
    const consumers = new Set(paths);
    const pending = [...paths];
    while (pending.length) {
      (reverseDependencies[pending.pop()] ?? []).forEach((consumer) => {
        if (!consumers.has(consumer)) { consumers.add(consumer); pending.push(consumer); }
      });
    }
    return [...consumers].sort();
  };
  const libraryFacts = (item) => {
    const declarations = libraryDeclarations(item.identifier);
    const direct = directConsumers(item.identifier);
    const resolved = [...new Set(item.modules ?? [])].sort();
    const usageKnown = data.metadata.options.includeLibraryUsageModules;
    const transitive = usageKnown ? resolved.filter((path) => !direct.includes(path)) : [];
    const totalConsumers = usageKnown ? resolved.length : direct.length;
    const hasDirect = direct.length > 0;
    const hasTransitive = transitive.length > 0;
    const source = hasDirect && hasTransitive ? "MIXED" : hasDirect ? "DIRECT" : hasTransitive ? "TRANSITIVE" : "UNKNOWN";
    return { declarations, direct, resolved, transitive, usageKnown, totalConsumers, impact: consumersOfModules(direct), source, scopes: [...new Set(declarations.map((item) => item.scope.toUpperCase()))] };
  };
  const librarySourceLabel = (source) => ({
    DIRECT: "Direct declaration",
    MIXED: "Direct + transitive",
    TRANSITIVE: "Transitive only",
    UNKNOWN: "Resolved library",
  })[source];

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
      return `<article class="finding ${item.severity.toLowerCase()}"><div class="finding-topline"><div><span class="finding-type">${escape(label(item.id))}</span><h3>${escape(item.subject)}</h3></div><span class="badge">${escape(item.severity)}</span></div><p>${escape(item.message)}</p>${item.dependencyPath.length ? `<p class="path">${item.dependencyPath.map(escape).join(" → ")}</p>` : ""}${item.declarations.length ? `<p class="path">Declared by: ${item.declarations.map(escape).join(", ")}</p>` : ""}${affected.length ? `<div class="related-entities"><span>Related modules</span>${affected.map(moduleLink).join("")}</div>` : ""}${item.suggestedFix ? `<p class="suggestion"><strong>Suggested fix:</strong> ${escape(item.suggestedFix)}</p>` : ""}</article>`;
    }).join("") : empty("No findings match these filters.");
  };

  const help = (description) => `<span class="help" tabindex="0" role="img" aria-label="${escape(description)}">i<span class="tooltip" role="tooltip">${escape(description)}</span></span>`;
  const entityList = (items, type, noItems) => items.length ? `<div class="entity-list">${[...items].sort((left, right) => left.localeCompare(right)).map((item) => type === "module" ? moduleLink(item) : libraryLink(item)).join("")}</div>` : empty(noItems);
  const detail = (title, items, type, noItems, count = items.length) => `<section class="detail-section"><h3>${title}<span>${count}</span></h3>${entityList(items, type, noItems)}</section>`;
  const renderModules = () => {
    const selected = module(state.selectedModule);
    const metricForSort = (item) => ({ DIRECT_DEPENDENCIES: item.directDependencies.length, TRANSITIVE_DEPENDENCIES: item.transitiveDependencyCount ?? item.transitiveDependencies.length, DIRECT_DEPENDENTS: item.directDependents.length, AFFECTED_MODULES: item.affectedModuleCount ?? item.affectedModules.length, LIBRARIES: directLibraryCounts[item.path] ?? 0 })[state.moduleSort];
    const visible = modules
      .filter((item) => item.path.toLowerCase().includes(state.moduleQuery.toLowerCase()))
      .sort((left, right) => {
        const direction = state.moduleSortDirection === "ASC" ? 1 : -1;
        const comparison = state.moduleSort === "NAME"
          ? left.path.localeCompare(right.path)
          : metricForSort(left) - metricForSort(right) || left.path.localeCompare(right.path);
        return comparison * direction;
      });
    $("module-list").innerHTML = visible.length ? visible.map((item) => `<button class="module-row ${item.path === state.selectedModule ? "active" : ""}" type="button" data-module="${escape(item.path)}"><strong>${escape(item.path)}</strong><div class="module-preview"><span>${item.directDependencies.length} direct dependencies</span><span>${item.transitiveDependencyCount ?? item.transitiveDependencies.length} indirect dependencies</span><span>${item.directDependents.length} direct consumers</span><span>${item.affectedModuleCount ?? item.affectedModules.length} all consumers</span><span>${directLibraryCounts[item.path] ?? 0} declared libraries</span><span>${item.projectCoverage.toFixed(1)}% impact</span></div></button>`).join("") : empty("No modules match this search.");
    if (!selected) { $("module-details").innerHTML = `<div class="details-placeholder"><h3>Select a module</h3><p>Choose any module to explore dependencies, impact, libraries, and findings.</p></div>`; return; }
    $("module-details").innerHTML = `<div class="details-title"><div><p class="eyebrow">SELECTED MODULE</p><h2><code>${escape(selected.path)}</code></h2></div><p class="project-impact">Project impact <strong>${selected.projectCoverage.toFixed(1)}%</strong>${help("Percentage of other modules that directly or indirectly depend on this module.")}</p></div><div class="detail-grid">${detail(`Direct module dependencies ${help("Modules this module declares as dependencies.")}`, selected.directDependencies, "module", "No direct module dependencies.")}${detail(`Indirect module dependencies ${help("Modules reachable through one or more dependency hops. Direct dependencies are excluded.")}`, selected.transitiveDependencies, "module", "No indirect module dependencies, or relationship lists are disabled in this report.", selected.transitiveDependencyCount ?? selected.transitiveDependencies.length)}${detail(`Direct consumers ${help("Modules that directly declare this module as a dependency.")}`, selected.directDependents, "module", "No direct consumers.")}${detail(`All consumers (change impact) ${help("All modules that directly or indirectly depend on this module. A change here can require them to rebuild, retest, or be reviewed.")}`, selected.affectedModules, "module", "No consumers are affected by changes to this module, or relationship lists are disabled in this report.", selected.affectedModuleCount ?? selected.affectedModules.length)}</div>${detail(`Resolved libraries ${help("External libraries resolved for this module. This optional report section may be disabled.")}`, selected.libraries, "library", "No resolved external libraries, or this optional report section is disabled.")}`;
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

  const renderLibraryWorkspace = () => {
    const hasConflict = (item) => data.libraries.versionConflicts.includes(item.identifier);
    const metric = (item, facts) => ({
      TOTAL_CONSUMERS: facts.totalConsumers,
      CHANGE_IMPACT: facts.impact.length,
      CONFLICT: hasConflict(item) ? 1 : 0,
    })[state.librarySort];
    const visible = libraries.map((item) => ({ item, facts: libraryFacts(item) }))
      .filter(({ item, facts }) =>
        [item.identifier, item.resolvedVersion || "", ...item.declaredVersions].join(" ").toLowerCase().includes(state.libraryQuery.toLowerCase())
        && (state.librarySource === "ALL" || facts.source === state.librarySource)
        && (state.libraryConflict === "ALL" || (state.libraryConflict === "CONFLICT") === hasConflict(item))
      )
      .sort((left, right) => {
        const direction = state.librarySortDirection === "ASC" ? 1 : -1;
        const comparison = state.librarySort === "NAME"
          ? left.item.identifier.localeCompare(right.item.identifier)
          : metric(left.item, left.facts) - metric(right.item, right.facts) || left.item.identifier.localeCompare(right.item.identifier);
        return comparison * direction;
      });
    $("library-count").textContent = visible.length + " of " + libraries.length + " resolved libraries";
    $("clear-library-filter").hidden = !state.libraryQuery;
    $("library-list").innerHTML = visible.length ? visible.map(({ item, facts }) => {
      const conflict = hasConflict(item);
      return '<button class="library-row ' + (item.identifier === state.selectedLibrary ? "active " : "") + (conflict ? "has-conflict" : "") + '" type="button" data-library="' + escape(item.identifier) + '"><div class="library-row-heading"><strong>' + escape(item.identifier) + '</strong><span class="status ' + (conflict ? "conflict" : "") + '">' + (conflict ? "Conflict" : "Aligned") + '</span></div><span class="library-version">Resolved ' + escape(item.resolvedVersion || "version unavailable") + '</span><div class="library-preview"><span>' + facts.declarations.length + ' direct declarations</span><span>' + facts.totalConsumers + ' consumers</span><span>' + facts.impact.length + ' change impact</span><span>' + librarySourceLabel(facts.source) + "</span></div></button>";
    }).join("") : empty("No libraries match these filters.");
    const selected = library(state.selectedLibrary);
    if (!selected) {
      $("library-details").innerHTML = '<div class="details-placeholder"><h3>Select a library</h3><p>Choose a library to trace declarations, consumers, versions, and change impact.</p></div>';
      return;
    }
    const facts = libraryFacts(selected);
    const conflict = hasConflict(selected);
    const declarations = facts.declarations.length
      ? '<div class="declaration-list">' + facts.declarations.map((item) => '<div class="declaration-row">' + moduleLink(item.module) + "<span>" + escape(item.configuration) + "</span><span>" + escape(item.scope) + "</span><code>" + escape(item.declaredVersion) + "</code></div>").join("") + "</div>"
      : empty("No direct declaration was found in the analysed modules.");
    const list = (title, items, message, count) => '<section class="detail-section"><h3>' + title + "<span>" + (count ?? items.length) + "</span></h3>" + entityList(items, "module", message) + "</section>";
    const resolved = facts.usageKnown
      ? list("Resolved consumers " + help("Modules where Gradle resolved this library, whether they declare it directly or receive it transitively."), facts.resolved, "No module resolved this library.", facts.totalConsumers)
      : list("Resolved consumers " + help("This relationship list was omitted to keep the JSON report compact. Enable includeLibraryUsageModules to include it."), [], "Resolved module usage is not included in this report.", facts.totalConsumers);
    const relatedFindings = findings.filter((item) => item.references?.libraries?.includes(selected.identifier));
    const findingsMarkup = relatedFindings.length
      ? '<div class="module-finding-list">' + relatedFindings.map((item) => '<div><span class="badge ' + item.severity.toLowerCase() + '">' + escape(item.severity) + "</span><strong>" + escape(label(item.id)) + "</strong><p>" + escape(item.message) + "</p></div>").join("") + "</div>"
      : empty("No findings are associated with this library.");
    $("library-details").innerHTML = '<div class="details-title"><div><p class="eyebrow">SELECTED LIBRARY</p><h2><code>' + escape(selected.identifier) + '</code></h2></div><div class="library-status-summary"><span class="status ' + (conflict ? "conflict" : "") + '">' + (conflict ? "Version conflict" : "Aligned") + "</span><p>" + librarySourceLabel(facts.source) + '</p></div></div><div class="library-detail-facts"><div><span>Direct declarations</span><strong>' + facts.declarations.length + '</strong></div><div><span>Total consumers</span><strong>' + facts.totalConsumers + '</strong></div><div><span>Change impact</span><strong>' + facts.impact.length + "</strong>" + help("Modules that directly declare this library, plus modules that directly or indirectly depend on them.") + '</div><div><span>Versions detected</span><strong>' + selected.declaredVersions.length + "</strong></div></div><section class=\"detail-section\"><h3>Direct declarations <span>" + facts.declarations.length + "</span></h3>" + declarations + '</section><div class="detail-grid">' + list("Direct declarer modules " + help("Modules with an explicit dependency declaration for this library."), facts.direct, "No direct declarer modules.") + list("Transitive consumers " + help("Modules that resolve this library without directly declaring it."), facts.transitive, facts.usageKnown ? "No transitive consumers." : "Transitive usage is not included in this report.", facts.usageKnown ? facts.transitive.length : 0) + list("Change impact " + help("Modules potentially affected when a direct declaration of this library changes."), facts.impact, "No modules are exposed through direct declarations.") + resolved + '</div><section class="detail-section"><h3>Version resolution <span>' + selected.declaredVersions.length + '</span></h3><div class="version-resolution"><div><span>Requested versions</span><strong>' + escape(selected.declaredVersions.length ? selected.declaredVersions.join(", ") : "No direct version request") + '</strong></div><div><span>Resolved version</span><strong>' + escape(selected.resolvedVersion || "Unavailable") + "</strong></div></div></section><section class=\"detail-section\"><h3>Related findings <span>" + relatedFindings.length + "</span></h3>" + findingsMarkup + "</section>";
    $("library-details").lastElementChild?.remove();
  };

  const renderLibraryDialog = () => {
    const selected = library(state.selectedLibrary);
    if (!selected) return;
    const visible = selected.modules.filter((path) => path.toLowerCase().includes(state.libraryModuleQuery.toLowerCase()));
    $("dialog-library-name").textContent = selected.identifier;
    $("dialog-module-list").innerHTML = visible.length ? visible.map((path) => `<button class="dialog-module" type="button" data-module="${escape(path)}"><code>${escape(path)}</code><span>${selected.directModules.includes(path) ? "Direct declaration" : "Transitive resolution"}</span></button>`).join("") : empty("No modules match this search.");
  };
  const selectModule = (path) => { state.selectedModule = path; state.moduleQuery = ""; $("module-filter").value = ""; renderModules(); setTab("modules"); };
  const openLibrary = (id) => { state.selectedLibrary = id; renderLibraryWorkspace(); setTab("libraries"); };

  document.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-tab]"); if (tab) setTab(tab.dataset.tab);
    const moduleTarget = event.target.closest("[data-module]"); if (moduleTarget) selectModule(moduleTarget.dataset.module);
    const libraryTarget = event.target.closest("[data-library]"); if (libraryTarget) openLibrary(libraryTarget.dataset.library);
  });
  $("finding-type").addEventListener("change", (event) => { state.findingType = event.target.value; renderFindings(); });
  $("finding-severity").addEventListener("change", (event) => { state.findingSeverity = event.target.value; renderFindings(); });
  $("module-filter").addEventListener("input", (event) => { state.moduleQuery = event.target.value; state.selectedModule = null; renderModules(); });
  $("module-sort").addEventListener("change", (event) => { state.moduleSort = event.target.value; renderModules(); });
  $("module-sort-direction").addEventListener("change", (event) => { state.moduleSortDirection = event.target.value; renderModules(); });
  $("library-filter").addEventListener("input", (event) => { state.libraryQuery = event.target.value; renderLibraryWorkspace(); });
  $("clear-library-filter").addEventListener("click", () => { state.libraryQuery = ""; $("library-filter").value = ""; renderLibraryWorkspace(); });
  $("library-source-filter").addEventListener("change", (event) => { state.librarySource = event.target.value; renderLibraryWorkspace(); });
  $("library-conflict-filter").addEventListener("change", (event) => { state.libraryConflict = event.target.value; renderLibraryWorkspace(); });
  $("library-sort").addEventListener("change", (event) => { state.librarySort = event.target.value; renderLibraryWorkspace(); });
  $("library-sort-direction").addEventListener("change", (event) => { state.librarySortDirection = event.target.value; renderLibraryWorkspace(); });

  const types = [...new Set(findings.map((item) => item.id))].sort();
  const severities = [...new Set(findings.map((item) => item.severity))].sort();
  $("finding-type").innerHTML = `<option value="ALL">All finding types</option>${types.map((type) => `<option value="${escape(type)}">${escape(label(type))}</option>`).join("")}`;
  $("finding-severity").innerHTML = `<option value="ALL">All severities</option>${severities.map((severity) => `<option value="${escape(severity)}">${escape(label(severity))}</option>`).join("")}`;
  renderFindings(); renderModules(); renderLibraryWorkspace();
})();
