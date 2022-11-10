import os
from pathlib import Path
from subprocess import check_output

if __name__ == '__main__':
    java = Path('src/main/java')
    files = [Path(root) / f for root, dir, files in os.walk(java) for f in files]

    for f in files:
        ftype = check_output(f'file {f}', shell=True).decode()
        if '8859' not in ftype:
            continue

        print(f'Converting {f} to UTF-8...')
        f.write_text(f.read_text('ISO-8859-1'), 'utf8')

    # print(files)
