"""
Uploads every built jar (versions/<mc>/build/libs, Fabric, and versions/<mc>-forge|neoforge/build/libs)
to Modrinth as a new version.

    set MODRINTH_TOKEN=...   (Modrinth > Settings > PATs, scopes: Create versions, Read projects)
    python modrinth-upload.py <project slug or id>            dry run: shows what would be uploaded
    python modrinth-upload.py <project slug or id> --upload   uploads
    --only=1.21.4,1.21.4-neoforge                            only these jar folders (a fix for some versions)

Versions that already exist on the project (same version number) are skipped, so it can be
re-run after a failure.
"""
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

API = 'https://api.modrinth.com/v2'
USER_AGENT = 'litematica-turbo-release (github.com/useleess/litematica-turbo)'
LITEMATICA_PROJECT = 'bEpr0Arc'
FORGEMATICA_PROJECT = 'dCKRaeBC'
CHANNEL = 'beta'

# jar folder -> Minecraft versions it runs on
GAME_VERSIONS = {
    '1.16.5': ['1.16.4', '1.16.5'],
    '1.17.1': ['1.17.1'],
    '1.18.2': ['1.18.2'],
    '1.19.4': ['1.19.4'],
    '1.20.1': ['1.20.1'],
    '1.20.4': ['1.20.3', '1.20.4'],
    '1.21': ['1.21', '1.21.1'],
    '1.21.4': ['1.21.4'],
    '1.21.10': ['1.21.9', '1.21.10'],
    '1.21.11': ['1.21.11'],
    '26.2': ['26.2'],
    '26.3': ['26.3'],
}

# Forge / NeoForge jar folder -> (loader, Minecraft versions, pinned Forgematica version id or None)
FORGE_TARGETS = {
    '1.16.5-forge': ('forge', ['1.16.5'], None),
    '1.17.1-forge': ('forge', ['1.17.1'], None),
    '1.18.2-forge': ('forge', ['1.18.2'], None),
    '1.19.4-forge': ('forge', ['1.19.4'], None),
    '1.20.1-forge': ('forge', ['1.20.1'], None),
    '1.20.4-neoforge': ('neoforge', ['1.20.4'], None),
    '1.21.1-neoforge': ('neoforge', ['1.21.1'], None),
    '1.21.4-neoforge': ('neoforge', ['1.21.4'], None),
    # Forgematica 0.4.5 for 1.21.10 has an invalid URL in its mods.toml and does not load; 0.4.3 does
    '1.21.10-neoforge': ('neoforge', ['1.21.10'], 'r4xJXkH8'),
    '1.21.11-neoforge': ('neoforge', ['1.21.11'], None),
    '26.2-neoforge': ('neoforge', ['26.2'], None),
}

CHANGELOG = """\
- Fixed: the Turbo tab in Litematica's config menu showed raw option names (turboEnabled ...)
  instead of "Turbo enabled" ... on this Minecraft version. Nothing else changed.
"""
FORGE_CHANGELOG = CHANGELOG


def mod_version():
    for line in open('gradle.properties', encoding='utf-8'):
        if line.strip().startswith('mod_version'):
            return line.split('=', 1)[1].strip()
    raise SystemExit('mod_version not found in gradle.properties')


def request(method, path, token, body=None, content_type=None):
    req = urllib.request.Request(API + path, data=body, method=method)
    req.add_header('User-Agent', USER_AGENT)
    req.add_header('Authorization', token)
    if content_type:
        req.add_header('Content-Type', content_type)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read() or b'null')
    except urllib.error.HTTPError as e:
        raise SystemExit('%s %s failed: %s %s' % (method, path, e.code, e.read().decode('utf-8', 'replace')))


def multipart(data, file_name, file_bytes):
    boundary = uuid.uuid4().hex
    parts = [
        ('--%s\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n' % boundary).encode(),
        json.dumps(data).encode(), b'\r\n',
        ('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\nContent-Type: application/java-archive\r\n\r\n'
         % (boundary, file_name, file_name)).encode(),
        file_bytes, b'\r\n',
        ('--%s--\r\n' % boundary).encode(),
    ]
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    upload = '--upload' in sys.argv
    only = [a.split('=', 1)[1].split(',') for a in sys.argv[1:] if a.startswith('--only=')]
    only = set(only[0]) if only else None

    if len(args) != 1:
        raise SystemExit(__doc__)

    token = os.environ.get('MODRINTH_TOKEN')
    token_file = os.path.join(os.path.expanduser('~'), '.modrinth-token')

    if not token and os.path.isfile(token_file):
        token = open(token_file, encoding='utf-8').read().strip()

    if not token:
        raise SystemExit('Set MODRINTH_TOKEN or put the token in %s first.' % token_file)

    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    version = mod_version()
    project = request('GET', '/project/' + args[0], token)
    existing = {v['version_number'] for v in request('GET', '/project/%s/version' % project['id'], token)}
    print('Project: %s (%s), %s; existing versions: %d' % (project['title'], project['id'], project.get('status'), len(existing)))

    targets = [(mc, 'fabric', mc, game_versions, None) for mc, game_versions in GAME_VERSIONS.items()]
    targets += [(folder, loader, folder.rsplit('-', 1)[0], game_versions, pin)
                for folder, (loader, game_versions, pin) in FORGE_TARGETS.items()]

    if only is not None:
        unknown = only - {t[0] for t in targets}
        if unknown:
            raise SystemExit('Unknown jar folders: %s' % ', '.join(sorted(unknown)))
        targets = [t for t in targets if t[0] in only]

    for folder, loader, mc, game_versions, pin in targets:
        file_name = 'litematica_turbo-%s-%s-%s.jar' % (loader, mc, version)
        path = os.path.join('versions', folder, 'build', 'libs', file_name)
        number = '%s+mc%s' % (version, mc) if loader == 'fabric' else '%s+mc%s-%s' % (version, mc, loader)

        if not os.path.isfile(path):
            raise SystemExit('Missing %s - build it first (./gradlew build, forge/gradlew -p forge build)' % path)

        if number in existing:
            print('skip   %-30s already on Modrinth' % number)
            continue

        if loader == 'fabric':
            name = 'Litematica Turbo %s for MC %s' % (version, mc)
            dependency = {'project_id': LITEMATICA_PROJECT, 'dependency_type': 'required'}
        else:
            name = 'Litematica Turbo %s for %s %s' % (version, 'NeoForge' if loader == 'neoforge' else 'Forge', mc)
            dependency = {'project_id': FORGEMATICA_PROJECT, 'dependency_type': 'required'}
            if pin:
                dependency['version_id'] = pin

        data = {
            'project_id': project['id'],
            'name': name,
            'version_number': number,
            'changelog': CHANGELOG if loader == 'fabric' else FORGE_CHANGELOG,
            'dependencies': [dependency],
            'game_versions': game_versions,
            'version_type': CHANNEL,
            'loaders': [loader],
            'featured': loader == 'fabric' and mc in ('1.20.4', '1.21.4', '1.21.11', '26.3'),
            'status': 'listed',
            'file_parts': [file_name],
            'primary_file': file_name,
        }

        if upload is False:
            pinned = '  Forgematica pinned to ' + pin if pin else ''
            print('would  %-30s %s  %-8s MC %s  (%d KB)%s' % (number, CHANNEL, loader, ', '.join(game_versions),
                                                           os.path.getsize(path) // 1024, pinned))
            continue

        body, content_type = multipart(data, file_name, open(path, 'rb').read())
        created = request('POST', '/version', token, body, content_type)
        # Modrinth requires the environment (v3 API, per version) before a project can be reviewed
        req = urllib.request.Request('https://api.modrinth.com/v3/version/' + created['id'], method='PATCH',
                                     data=json.dumps({'environment': 'client_only'}).encode())
        req.add_header('User-Agent', USER_AGENT)
        req.add_header('Authorization', token)
        req.add_header('Content-Type', 'application/json')
        urllib.request.urlopen(req)
        print('upload %-30s -> %s' % (number, created['id']))

    if upload is False:
        print('\nDry run only. Add --upload to publish.')


if __name__ == '__main__':
    main()
