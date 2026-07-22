"""将 Java switch 中 case R.xxx 批量替换为 if-else"""
import re, os, sys

def has_r_case(lines, start, end):
    for i in range(start, end):
        if re.search(r'case\s+R\.\w+\.\w+\s*:', lines[i]):
            return True
    return False

def convert_file(content):
    lines = content.split('\n')
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r'^(\s*)switch\s*\((.+)\)\s*\{?', line)
        if not m:
            result.append(line)
            i += 1
            continue
        indent, expr = m.group(1), m.group(2).strip()
        brace = line.count('{') - line.count('}')
        s = i + 1
        e = s
        for j in range(s, len(lines)):
            brace += lines[j].count('{') - lines[j].count('}')
            if brace <= 0:
                e = j
                break
        if not has_r_case(lines, s, e):
            result.append(line)
            i += 1
            continue
        result.append(f'{indent}int __id = {expr};')
        first = False
        for k in range(s, e):
            bl = lines[k]
            st = bl.strip()
            cm = re.match(r'case\s+(R\.\w+\.\w+)\s*:', st)
            dm = re.match(r'default\s*:', st)
            if cm:
                cid = cm.group(1)
                if not first:
                    result.append(f'{indent}if (__id == {cid}) {{')
                    first = True
                else:
                    result.append(f'{indent}}} else if (__id == {cid}) {{')
                after = st.split(':', 1)[1].strip()
                if after and after != 'break;':
                    result.append(f'{indent}    {after}')
            elif dm:
                result.append(f'{indent}}} else {{')
                after = st.split(':', 1)[1].strip()
                if after and after != 'break;':
                    result.append(f'{indent}    {after}')
            else:
                if st == 'break;' or not st:
                    continue
                result.append(bl)
        result.append(f'{indent}}}')
        i = e + 1
    return '\n'.join(result)

def process_file(fp):
    with open(fp, 'r', encoding='utf-8') as f:
        c = f.read()
    nc = convert_file(c)
    if nc != c:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(nc)
        print(f'  OK {os.path.relpath(fp)}')
        return True
    return False

d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../app/src/main/java')
d = os.path.abspath(d)
n = 0
for root, dirs, files in os.walk(d):
    for f in files:
        if f.endswith('.java'):
            if process_file(os.path.join(root, f)):
                n += 1
print(f'Modified {n} files')
