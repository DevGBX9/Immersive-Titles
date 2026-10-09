"""
A script to report the Minecraft version matrix of this project:
the enabled versions (which are the gradle subprojects that get built)
and the disabled versions (kept around so that they can be re-enabled later).

The output is markdown, so it can be piped into $GITHUB_STEP_SUMMARY.
"""
__author__ = 'DevGBX9'

import json


def main():
	with open('settings.json') as f:
		settings: dict = json.load(f)

	enabled: list[str] = settings['versions']
	disabled: list[str] = settings.get('disabled_versions', [])

	with open('versions/mainProject') as f:
		core_project = f.read().strip()

	print('## Minecraft Version Matrix')
	print('')
	print('| Version | Status |')
	print('| --- | --- |')
	for version in enabled:
		status = 'enabled - core project, the shared sources in `src/` are written for it' \
			if version == core_project else 'enabled'
		print('| `{}` | {} |'.format(version, status))
	for version in disabled:
		print('| `{}` | disabled for now |'.format(version))
	print('')
	print('Enabled build matrix: {}'.format(', '.join('`{}`'.format(v) for v in enabled)))
	print('')
	print('Disabled for now (configuration kept, can be re-enabled later): {}'.format(
		', '.join('`{}`'.format(v) for v in disabled)))
	print('')
	print('See `settings.json`, the `preprocess` block in `build.gradle` and `versions/mainProject` '
		'for how to enable or disable a version.')


if __name__ == '__main__':
	main()
