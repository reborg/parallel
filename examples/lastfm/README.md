# Lastfm dataset processing example

This example project is designed to showcase Parallel mainly in the context of intensive data IO.

## Introduction

To run the example, you need to download the Last.fm dataset. This is an (old but good) version of the Last.fm data kindly hosted by [Oscar Celma](http://ocelma.net). If you are interested in music recommendation in general, have a look around the web site or [buy his book](http://ocelma.net/MusicRecommendationBook/index.html).

The project answers a list of interesting questions about music using the Last.fm dataset. It first shows how to retrieve the answers with plain Clojure (`src/lastfm/plain.clj`) and then how we could speed up processing using the Parallel library (`src/lastfm/parallel.clj`).

### Download the data

Sizes: lastfm-dataset-1K.tar.gz (~641Mb), lastfm-dataset-360K.tar.gz (~543Mb) but both files expands into much larger ones (2.4G and 1.6G respectively).

```bash
mkdir data; cd data
curl -O http://mtg.upf.edu/static/datasets/last.fm/lastfm-dataset-1K.tar.gz
curl -O http://mtg.upf.edu/static/datasets/last.fm/lastfm-dataset-360K.tar.gz
tar xvfz lastfm-dataset-1K.tar.gz
tar xvfz lastfm-dataset-360K.tar.gz
cd lastfm-dataset-1K
head -n 1000 userid-timestamp-artid-artname-traid-traname.tsv > small.tsv
cd ..
cd lastfm-dataset-360K
head -n 1000 usersha1-artmbid-artname-plays.tsv > small.tsv
cd ..
```

The instructions above also creates `small.tsv` samples of only 1k lines for quick experiments.
