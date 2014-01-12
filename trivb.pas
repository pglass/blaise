{ First test program       -- file trivb.pas }


{ Results should look like this:
 yyparse result =        0
 Symbol table level 0   ... printed out so you can see it. }
{Symbol table level 1
 237608           i  VAR    typ integer  lvl  1  siz     4  off     4     }
{237688         lim  VAR    typ integer  lvl  1  siz     4  off     8
 token 239160  OP       program  dtype  0  link      0  operands 237032
}

{
(program graph1 (progn output)
                (progn (:= lim 7)
                       (progn (:= i 0)
                              (label 0)                        }
{                             (if (<= i lim)
                                  (progn (funcall writeln '*')
                                         (:= i (+ i 1))
                                         (goto 0))))))         }

program graph1(output);
var i,lim : integer;
begin
   lim := 7;
   for i := 0 to lim do
      writeln('*')
end.
