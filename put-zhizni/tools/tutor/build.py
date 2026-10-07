import csv,re,json,collections,sys,os
H=os.path.dirname(os.path.abspath(__file__)); D=H+'/../dict/'; OUT=sys.argv[1]
TOP={'basics':'Основы','people':'Люди и семья','body':'Тело и здоровье','food':'Еда','home':'Дом','clothes':'Одежда',
 'city':'Город','travel':'Путешествия','work':'Работа','money':'Деньги','study':'Учёба','time':'Время','nature':'Природа и погода',
 'animals':'Животные','feelings':'Чувства','talk':'Общение','hobby':'Хобби и отдых','art':'Культура и искусство','tech':'Техника',
 'verbs':'Главные глаголы','adj':'Прилагательные','grammar':'Служебные слова и числа'}
def freqrank(f):
    r={}
    for i,l in enumerate(open(D+f,encoding='utf-8')):
        w=l.split()[0]
        r.setdefault(w,i)
    return r
def level(rank):
    if rank is None: return 3
    return 1 if rank<1000 else 2 if rank<2500 else 3 if rank<5000 else 4 if rank<9000 else 5
def core(lang):
    out=[];seen=set();t=None
    for n in (1,2,3,4):
        p=f'{H}/{lang}_{n}.txt'
        if not os.path.exists(p): continue
        for l in open(p,encoding='utf-8'):
            l=l.strip()
            if not l: continue
            if l.startswith('#'): t=l[1:]; continue
            w,ru,ex,exr=l.split('|')
            if w.lower() in seen: continue
            seen.add(w.lower()); out.append([w,ru,ex,exr,t])
    return out
def headword(w,lang):
    w=re.sub(r'\s*\([^)]*\)$','',w)
    w=re.sub(r'^(el|la|los|las|le|la|les|l\x27)\s*','',w) if lang!='en' else w
    return w.strip('¿?¡! ').lower()
# ---------- EN->RU (OpenRussian, обратный) ----------
POS={'nouns.csv':'n','verbs.csv':'v','adjectives.csv':'adj','others.csv':'x'}
en2ru=collections.defaultdict(list)
def clean(g):
    g=re.sub(r'\([^)]*\)','',g).strip().lower()
    g=re.sub(r'^(to|a|an|the) ','',g).strip(' .!?')
    return g
def stress(a): return re.sub(r"([аеёиоуыэюяАЕЁИОУЫЭЮЯ])'",lambda m:m.group(1)+'́',a).replace("'","")
for f,p in POS.items():
    r=list(csv.reader(open(D+f,encoding='utf-8'),delimiter='\t'))
    for k,row in enumerate(r[1:]):
        bare,acc,tr=row[0],row[1],row[2]
        if not tr: continue
        for si,s in enumerate(tr.split(';')):
            for gi,g in enumerate(s.split(',')):
                g=clean(g)
                if not g or len(g.split())>2 or not re.match(r"^[a-z][a-z' -]*$",g): continue
                en2ru[g].append((k+si*400+gi*150,acc or bare,p,bare))
def ru_for(g,n=3,pos=None):
    lst=sorted(en2ru.get(g,[]));res=[];b=set()
    for sc,acc,p,bare in lst:
        if bare in b or (pos and p!=pos): continue
        b.add(bare);res.append(stress(acc))
        if len(res)>=n: break
    return res
# ---------- CMU -> IPA ----------
A={'AA':'ɑ','AE':'æ','AH':'ʌ','AO':'ɔ','AW':'aʊ','AY':'aɪ','B':'b','CH':'tʃ','D':'d','DH':'ð','EH':'e','ER':'ɜr','EY':'eɪ','F':'f','G':'ɡ','HH':'h','IH':'ɪ','IY':'iː','JH':'dʒ','K':'k','L':'l','M':'m','N':'n','NG':'ŋ','OW':'oʊ','OY':'ɔɪ','P':'p','R':'r','S':'s','SH':'ʃ','T':'t','TH':'θ','UH':'ʊ','UW':'uː','V':'v','W':'w','Y':'j','Z':'z','ZH':'ʒ'}
cmu={}
for l in open(D+'cmudict.dict',encoding='utf-8'):
    p=l.split('#')[0].split()
    if not p or '(' in p[0] or p[0] in cmu: continue
    seg=[]
    for ph in p[1:]:
        st=ph[-1] if ph[-1].isdigit() else ''
        b=ph.rstrip('012')
        v=A.get(b,'')
        if b=='AH' and st=='0': v='ə'
        if b=='ER' and st=='0': v='ər'
        if st=='1':
            vow=[i for i,(x,v) in enumerate(seg) if v or x in 'ɑæʌɔeɪiʊuəaoɜ' and x]
            k=0 if not vow else (len(seg)-1 if seg and not seg[-1][1] else len(seg))
            seg.insert(k,('ˈ',True))
        seg.append((v,bool(st) or b in ('AA','AE','AH','AO','AW','AY','EH','ER','EY','IH','IY','OW','OY','UH','UW')))
    cmu[p[0]]=''.join(x for x,_ in seg)
def ipa(w):
    parts=[cmu.get(x) for x in re.findall(r"[a-z']+",w.lower())]
    return ' '.join(parts) if parts and all(parts) else ''
# ---------- examples from tatoeba pairs (en/es) ----------
exEN={};exES={}
for l in open(D+'es_sentences.tsv',encoding='utf-8'):
    c=l.rstrip('\n').split('\t')
    if len(c)<6: continue
    en,es,tags=c[0],c[1],c[5]
    if len(en)>60 or len(es)>60: continue
    for w in set(re.findall(r"[a-z']+",en.lower())):
        if w not in exEN or len(en)<len(exEN[w][0]): exEN[w]=(en,es)
    for m in re.finditer(r':[\w-]+,([^ ]+)',tags):
        for lem in m.group(1).split('|')[-1:]:
            lem=lem.lower()
            if lem not in exES or len(es)<len(exES[lem][0]): exES[lem]=(es,en)
def esc(o): return json.dumps(o,ensure_ascii=False,separators=(',',':'))
def write(lang,var,words):
    open(f'{OUT}/content-tutor-{lang}.js','w',encoding='utf-8').write(
      f'/* Словарь репетитора ({lang}). Поля: [слово, перевод, пример, перевод примера, тема, уровень 1-5, транскрипция, ранг частоты]. '
      'Базовые темы составлены вручную; расширенная часть — OpenRussian (CC-BY-SA), Wiktionary через doozan/spanish_data (CC-BY-SA), примеры Tatoeba (CC-BY 2.0 FR), частоты hermitdave/FrequencyWords (MIT), CMUdict (BSD). */\n'
      f'window.{var}={esc({"topics":TOP,"w":words})};\n')
    print(lang,len(words))
# ---------- EN ----------
fr_en=freqrank('en_50k.txt'); words=[]; have=set()
for w,ru,ex,exr,t in core('en'):
    h=headword(w,'en'); rk=fr_en.get(h.split()[0] if h else h)
    words.append([w,ru,ex,exr,t,min(level(rk),3) if t else level(rk),ipa(h),rk if rk is not None else 99999]); have.add(h)
vocab=set(fr_en)
def inflected(w):
    if w.endswith('ies') and w[:-3]+'y' in vocab: return True
    if w.endswith('es') and w[:-2] in vocab and w[:-2] in en2ru: return True
    if w.endswith('s') and not w.endswith('ss') and w[:-1] in en2ru: return True
    if w.endswith('ing') and (w[:-3] in en2ru or w[:-3]+'e' in en2ru or (len(w)>5 and w[-4]==w[-5] and w[:-4] in en2ru)): return True
    if w.endswith('ed') and (w[:-2] in en2ru or w[:-1] in en2ru or (len(w)>4 and w[-3]==w[-4] and w[:-3] in en2ru) or (w.endswith('ied') and w[:-3]+'y' in en2ru)): return True
    if w.endswith('er') and w[:-2] in en2ru and w[:-2]+'er' not in ('water','paper'): return False
    return False
for w,rk in sorted(fr_en.items(),key=lambda x:x[1]):
    if rk>20000: break
    if w in have or len(w)<2 or not re.match(r"^[a-z]+$",w) or w not in en2ru or inflected(w): continue
    ru=ru_for(w)
    if not ru: continue
    e=exEN.get(w,('',''))
    words.append([w,', '.join(ru),e[0],'',None,level(rk),ipa(w),rk]); have.add(w)
write('en','TUTOR_EN',words)
# ---------- ES ----------
fr_es={}; pos_es={}
for i,row in enumerate(csv.DictReader(open(D+'es_frequency.csv',encoding='utf-8'))):
    if row['flags']: continue
    fr_es.setdefault(row['spanish'],i); pos_es.setdefault(row['spanish'],row['pos'])
gl={};gen={}
cur=None
for l in open(D+'es_es-en.data',encoding='utf-8'):
    if l.startswith('_____'): cur=None; continue
    if cur is None and l.strip() and not l.startswith(' ') and ':' not in l[:5]: cur=l.strip(); continue
    if cur is None: continue
    s=l.strip()
    if s.startswith('g: ') and cur not in gen: gen[cur]=s[3:].strip()
    if l.startswith('  gloss: ') and cur not in gl:
        g=s[7:].strip()
        if re.search(r'(form|plural|spelling|abbreviation|alternative) of',g): gl[cur]=None; continue
        gl[cur]=g
    elif l.startswith('  gloss: ') and gl.get(cur) is None and cur in gl: pass
words=[];have=set()
for w,ru,ex,exr,t in core('es'):
    h=headword(w,'es'); rk=fr_es.get(h)
    words.append([w,ru,ex,exr,t,min(level(rk),3),'',rk if rk is not None else 99999]); have.add(h)
for w,rk in sorted(fr_es.items(),key=lambda x:x[1]):
    if rk>15000: break
    if w in have or not gl.get(w) or not re.match(r"^[a-záéíóúñü]+$",w): continue
    g=gl[w]; first=re.split(r'[;,]',re.sub(r'\([^)]*\)','',g))[0].strip().lower()
    first=re.sub(r'^(to|a|an|the) ','',first).strip(' .')
    P={'v':'v','n':'n','adj':'adj','adv':'x'}.get(pos_es.get(w))
    if not P: continue
    ru=ru_for(first,2,P)
    if not ru: continue
    g=re.sub(r'\s*\([^)]*\)','',g).strip()
    head=w
    if pos_es.get(w)=='n':
        gg=gen.get(w,'')
        head=('la ' if gg.startswith('f') else 'el ' if gg.startswith('m') else '')+w
    e=exES.get(w,('',''))
    words.append([head,', '.join(ru)+' · en: '+g[:60],e[0],('EN: '+e[1]) if e[1] else '',None,level(rk),'',rk]); have.add(w)
write('es','TUTOR_ES',words)
# ---------- FR ----------
fr_fr=freqrank('fr_50k.txt'); words=[]
for w,ru,ex,exr,t in core('fr'):
    h=headword(w,'fr').split()[0] if headword(w,'fr') else ''; rk=fr_fr.get(h)
    words.append([w,ru,ex,exr,t,min(level(rk),3),'',rk if rk is not None else 99999])
write('fr','TUTOR_FR',words)
