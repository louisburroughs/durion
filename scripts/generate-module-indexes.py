#!/usr/bin/env python3
from pathlib import Path

backend = Path('/home/n541342/IdeaProjects/durion-positivity-backend')
durion = Path('/home/n541342/IdeaProjects/durion')

for module in sorted(backend.glob('pos-*')):
    if not module.is_dir():
        continue
    index = module / 'index.md'
    entries = []
    entries.append('* [README.md](README.md) - module overview and setup')
    if (module / 'src').exists():
        entries.append('* [src/](src/) - source tree')
    for child in sorted(module.iterdir()):
        if child.name in {'README.md', 'src', 'target', '.git', '.idea'}:
            continue
        if child.is_dir() and child.name != 'target':
            entries.append(f'* [{child.name}/]({child.name}/) - {child.name} workspace area')
    if not entries:
        entries.append('* [module root](.) - project root')
    index.write_text('# ' + module.name + '\n\n' + '\n'.join(entries) + '\n', encoding='utf-8')

for domain in sorted((durion / 'domains').iterdir()):
    if not domain.is_dir() or domain.name.startswith('.'):
        continue
    index = domain / 'index.md'
    entries = []
    if (domain / 'README.md').exists():
        entries.append('* [README.md](README.md) - domain overview')
    for child in sorted(domain.iterdir()):
        if child.is_dir() and not child.name.startswith('.') and child.name not in {'.git', '.idea'}:
            entries.append(f'* [{child.name}/]({child.name}/) - domain subfolder')
    if not entries:
        entries.append('* [domain root](.) - domain root')
    index.write_text('# ' + domain.name + '\n\n' + '\n'.join(entries) + '\n', encoding='utf-8')
