
/*
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

var e={comments:{lineComment:"#"},brackets:[["{","}"],["[","]"],["(",")"]],autoClosingPairs:[{open:"{",close:"}"},{open:"[",close:"]"},{open:"(",close:")"},{open:'"',close:'"'},{open:"'",close:"'"},{open:"`",close:"`"}],surroundingPairs:[{open:"{",close:"}"},{open:"[",close:"]"},{open:"(",close:")"},{open:'"',close:'"'},{open:"'",close:"'"},{open:"`",close:"`"}]},r={defaultToken:"",ignoreCase:!0,tokenPostfix:".shell",brackets:[{token:"delimiter.bracket",open:"{",close:"}"},{token:"delimiter.parenthesis",open:"(",close:")"},{token:"delimiter.square",open:"[",close:"]"}],keywords:["if","then","do","else","elif","while","until","for","in","esac","fi","fin","fil","done","exit","set","unset","export","function"],builtins:["ab","awk","bash","beep","cat","cc","cd","chown","chmod","chroot","clear","cp","curl","cut","diff","echo","find","gawk","gcc","get","git","grep","hg","kill","killall","ln","ls","make","mkdir","openssl","mv","nc","node","npm","ping","ps","restart","rm","rmdir","sed","service","sh","shopt","shred","source","sort","sleep","ssh","start","stop","su","sudo","svn","tee","telnet","top","touch","vi","vim","wall","wc","wget","who","write","yes","zsh"],symbols:/[=><!~?&|+\-*\/\^;\.,]+/,tokenizer:{root:[{include:"@whitespace"},[/[a-zA-Z]\w*/,{cases:{"@keywords":"keyword","@builtins":"type.identifier","@default":""}}],{include:"@strings"},{include:"@parameters"},{include:"@heredoc"},[/[{}\[\]()]/,"@brackets"],[/-+\w+/,"attribute.name"],[/@symbols/,"delimiter"],{include:"@numbers"},[/[,;]/,"delimiter"]],whitespace:[[/\s+/,"white"],[/(^#!.*$)/,"metatag"],[/(^#.*$)/,"comment"]],numbers:[[/\d*\.\d+([eE][\-+]?\d+)?/,"number.float"],[/0[xX][0-9a-fA-F_]*[0-9a-fA-F]/,"number.hex"],[/\d+/,"number"]],strings:[[/'/,"string","@stringBody"],[/"/,"string","@dblStringBody"]],stringBody:[[/'/,"string","@popall"],[/./,"string"]],dblStringBody:[[/"/,"string","@popall"],[/./,"string"]],heredoc:[[/(<<[-<]?)(\s*)(['"`]?)([\w\-]+)(['"`]?)/,["constants","white","string.heredoc.delimiter","string.heredoc","string.heredoc.delimiter"]]],parameters:[[/\$\d+/,"variable.predefined"],[/\$\w+/,"variable"],[/\$[*@#?\-$!0_]/,"variable"],[/\$'/,"variable","@parameterBodyQuote"],[/\$"/,"variable","@parameterBodyDoubleQuote"],[/\$\(/,"variable","@parameterBodyParen"],[/\$\{/,"variable","@parameterBodyCurlyBrace"]],parameterBodyQuote:[[/[^#:%*@\-!_']+/,"variable"],[/[#:%*@\-!_]/,"delimiter"],[/[']/,"variable","@pop"]],parameterBodyDoubleQuote:[[/[^#:%*@\-!_"]+/,"variable"],[/[#:%*@\-!_]/,"delimiter"],[/["]/,"variable","@pop"]],parameterBodyParen:[[/[^#:%*@\-!_)]+/,"variable"],[/[#:%*@\-!_]/,"delimiter"],[/[)]/,"variable","@pop"]],parameterBodyCurlyBrace:[[/[^#:%*@\-!_}]+/,"variable"],[/[#:%*@\-!_]/,"delimiter"],[/[}]/,"variable","@pop"]]}};export{e as conf,r as language};
