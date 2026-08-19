#!/usr/bin/env python3
from pathlib import Path
import datetime

root = Path('/home/n541342/IdeaProjects/durion')
backend_root = Path('/home/n541342/IdeaProjects/durion-positivity-backend')
cat_root = root / 'knowledge-catalog'

# Prepare bundle structure
(cat_root / 'adr').mkdir(exist_ok=True)
(cat_root / 'domains').mkdir(exist_ok=True)
(cat_root / 'backend').mkdir(exist_ok=True)

base_github = 'https://github.com/louisburroughs'


def title_from_name(name: str) -> str:
    return name.replace('-', ' ').replace('_', ' ').title()


# Root files
(cat_root / 'index.md').write_text(
    "---\nokf_version: 0.2\n---\n\n# Durion Knowledge Catalog\n\n* [ADRs](/adr/)\n* [Domains](/domains/)\n* [Backend Modules](/backend/)\n",
    encoding='utf-8',
)
(cat_root / 'log.md').write_text(
    "# Directory Update Log\n\n## 2026-08-19\n* **Initialization**: Established the OKF knowledge catalog for `durion` and the backend module suite.\n",
    encoding='utf-8',
)

# ADR concepts
adr_dir = root / 'docs' / 'adr'
adr_files = sorted(adr_dir.glob('*.adr.md'))
for adr in adr_files:
    concept_name = adr.stem.replace('.adr', '')
    title = title_from_name(concept_name.replace('-', ' ').replace('_', ' '))
    concept_path = cat_root / 'adr' / f'{concept_name}.md'
    concept_path.write_text(
        f"---\ntype: ADR\ntitle: {title}\ndescription: Pointer to the canonical ADR for {title}.\nresource: {base_github}/durion/blob/main/docs/adr/{adr.name}\ntags: [adr, governance, architecture]\ngenerated: {{ by: human:louisburroughs, at: 2026-08-19T00:00:00Z }}\n---\n\nSee the [canonical ADR]({base_github}/durion/blob/main/docs/adr/{adr.name}) for the decision record and rationale.\n",
        encoding='utf-8',
    )

( cat_root / 'adr' / 'index.md').write_text(
    "# ADR Index\n\n" + "\n".join(f"* [{p.stem.replace('.adr', '')}]({p.name}) - Pointer concept for the canonical ADR" for p in sorted((cat_root / 'adr').glob('*.md')) if p.name != 'index.md') + "\n",
    encoding='utf-8',
)

# Domain concepts
for domain in sorted((root / 'domains').iterdir()):
    if domain.is_dir() and not domain.name.startswith('.'):
        resource = f'{base_github}/durion/blob/main/domains/{domain.name}'
        title = title_from_name(domain.name)
        concept_path = cat_root / 'domains' / f'{domain.name}.md'
        concept_path.write_text(
            f"---\ntype: Domain\ntitle: {title}\ndescription: Pointer concept for the {title} domain in the workspace.\nresource: {resource}\ntags: [domain, {domain.name}]\ngenerated: {{ by: human:louisburroughs, at: 2026-08-19T00:00:00Z }}\n---\n\nSee the [domain folder]({resource}) for domain docs and capability definitions.\n",
            encoding='utf-8',
        )

(cat_root / 'domains' / 'index.md').write_text(
    "# Domain Index\n\n" + "\n".join(f"* [{p.stem}]({p.name}) - Pointer concept for the domain" for p in sorted((cat_root / 'domains').glob('*.md')) if p.name != 'index.md') + "\n",
    encoding='utf-8',
)

# Backend module concepts
for module in sorted(backend_root.glob('pos-*')):
    if module.is_dir():
        title = title_from_name(module.name.replace('pos-', ''))
        resource = f'{base_github}/durion-positivity-backend/blob/main/{module.name}'
        concept_path = cat_root / 'backend' / f'{module.name}.md'
        concept_path.write_text(
            f"---\ntype: Module\ntitle: {title}\ndescription: Pointer concept for the {title} backend module.\nresource: {resource}\ntags: [backend, module, {module.name}]\ngenerated: {{ by: human:louisburroughs, at: 2026-08-19T00:00:00Z }}\n---\n\nSee the [module directory]({resource}) for implementation details, docs, and build configuration.\n",
            encoding='utf-8',
        )

(cat_root / 'backend' / 'index.md').write_text(
    "# Backend Module Index\n\n" + "\n".join(f"* [{p.stem}]({p.name}) - Pointer concept for the module" for p in sorted((cat_root / 'backend').glob('*.md')) if p.name != 'index.md') + "\n",
    encoding='utf-8',
)
