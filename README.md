What if you turned AI on yourself? What data might you use?

I propose labeling 'Rorschach pairs' of pictures interesting or not according to one's taste. Small models predict interesting unseen pairs with 90% accuracy within the set of pictures, implying a quantitative model of personality. The process of labeling itself is a new kind of statistical-moral solitaire for exploring how one's mind works, starting by doing 'compare and contrast' on the pairs of photos. Weighing pairs leads to clashes in rationales, introducing classification in a nitty-gritty, personal way. I hypothesize that increasing such decisions will have a measurable effect on personality. This might be a provable form of work for general educational, economic and social purposes, possibly the first of many such apps in different media.

You'll own the data, and have a grasp to accelerate understanding any new ML you might throw at it, not to mention personal insights ML may give. I call this application 'Rorschach pairs', since the graph formed by 'connections' of interestingly-paired photos is a graph of personality that enables quantitative approaches to Rorschach's notion of projection. (As several wise old LLMs told me in the 60's, "Dude, it could blow your mind!")

The setup should become more user-friendly, starting from this programmer-level description:

    Copy photos to an images folder. 
    Run scripts/programs to extract Color information and Imagenet vectors. 
    Create postgres database, with pgvector library. 
    Import photo info into database. 
    Compile/start web server. 
    View/label photo pairs in browser. 
    Train models, predict pairs, view.

See https://github.com/phobrain/Phobrain/blob/main/pr/INSTALL

The (Java) web server is intended for local use as a default for data protection. It is old school, and I hope to see AGPL-licensed rewrites in new languages. Much data prep code is also in Java, which is used for its fantastic parallelism compared to python. Training/prediction use keras; much optimization could be done for batch predictions.

Neural nets: color histograms and Imagenet vectors are used to represent photos. Pairs labeled 0/1 train nets to ~85% accuracy. Then vectors of size 2..16 are generated for the photos using those nets (these are personal pair analogs of Imagenet vectors), and groups of these smaller vectors are again taken to represent the photos, using the original labeled pairs to train miniscule nets that predict interesting photos with ~90% accuracy.

Observation: the gold standard for labeling is to use randomly-generated pairs. Alternative methods are provided for variety.


