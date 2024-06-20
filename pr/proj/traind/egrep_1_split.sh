# == train on 1, test on others

egrep "^1/" train.pos | egrep " 1/" > train1/train.pos
egrep "^1/" train.neg | egrep " 1/" > train1/train.neg
egrep -v "^1/" test.pos | egrep -v " 1/" > train1/test.pos
egrep -v "^1/" test.neg | egrep -v " 1/" > train1/test.neg

# == train on other, test on 1

egrep -v "^1/" train.pos | egrep -v " 1/" > test1/train.pos
egrep -v "^1/" train.neg | egrep -v " 1/" > test1/train.neg
egrep "^1/" test.pos | egrep " 1/" > test1/test.pos
egrep "^1/" test.neg | egrep " 1/" > test1/test.neg
