"""Read FTB's language-only compounds in JSON or its comma-optional SNBT output."""
import json
import re

def read_language(path):
    text=path.read_text('utf-8-sig')
    try:
        result=json.loads(text)
    except json.JSONDecodeError:
        pos=0
        decoder=json.JSONDecoder(strict=False)

        def skip():
            nonlocal pos
            while pos<len(text):
                if text[pos].isspace() or text[pos]==',': pos+=1
                elif text[pos]=='#':
                    end=text.find('\n',pos)
                    pos=len(text) if end<0 else end+1
                else: break

        def take(char):
            nonlocal pos
            skip()
            if pos>=len(text) or text[pos]!=char:
                raise ValueError(f'{path}: expected {char!r} at character {pos}')
            pos+=1

        def string():
            nonlocal pos
            skip()
            if pos>=len(text) or text[pos]!='"':
                raise ValueError(f'{path}: expected a quoted language string at character {pos}')
            value,pos=decoder.raw_decode(text,pos)
            return value

        take('{');result={}
        while True:
            skip()
            if pos<len(text) and text[pos]=='}': pos+=1;break
            if pos<len(text) and text[pos]=='"': key=string()
            else:
                match=re.match(r'[A-Za-z0-9_.+\-]+',text[pos:])
                if match is None: raise ValueError(f'{path}: invalid language key at character {pos}')
                key=match.group();pos+=len(key)
            take(':');skip()
            if pos<len(text) and text[pos]=='[':
                pos+=1;value=[]
                while True:
                    skip()
                    if pos<len(text) and text[pos]==']': pos+=1;break
                    value.append(string())
            else: value=string()
            if key in result: raise ValueError(f'{path}: duplicate language key {key}')
            result[key]=value
        skip()
        if pos!=len(text): raise ValueError(f'{path}: unexpected trailing language content')
    if not isinstance(result,dict) or any(not isinstance(key,str) or not (
            isinstance(value,str) or isinstance(value,list) and all(isinstance(line,str) for line in value))
            for key,value in result.items()):
        raise ValueError(f'{path}: expected language strings or lists of strings')
    return result
