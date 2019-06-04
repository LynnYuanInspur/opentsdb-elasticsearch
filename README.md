       ___                 _____ ____  ____  ____
      / _ \ _ __   ___ _ _|_   _/ ___||  _ \| __ )
     | | | | '_ \ / _ \ '_ \| | \___ \| | | |  _ \
     | |_| | |_) |  __/ | | | |  ___) | |_| | |_) |
      \___/| .__/ \___|_| |_|_| |____/|____/|____/
           |_|    The modern time series database.


[![Build Status](https://travis-ci.org/OpenTSDB/opentsdb-elasticsearch.svg?branch=master)](https://travis-ci.org/OpenTSDB/opentsdb-elasticsearch) [![Coverage Status](https://coveralls.io/repos/github/OpenTSDB/opentsdb-elasticsearch/badge.svg?branch=master)](https://coveralls.io/github/OpenTSDB/opentsdb-elasticsearch?branch=master)
 
Search plugin for OpenTSDB

##Installation

* Compile the plugin via ``mvn package``.
* Create a plugins directory for your TSD
* Copy the plugin from the ``target`` directory into your TSD's plugin's directory.
* Add the following configs to your ``opentsdb.conf`` file.
    * Add ``tsd.core.plugin_path = <directory>`` pointing to a valid directory for your plugins.
    * Add ``tsd.search.enable = true``
    * Add ``tsd.search.plugin = net.opentsdb.search.ElasticSearch`` 
    * Add ``tsd.search.elasticsearch.host = <host>`` The HTTP protocol, host and port for an ES host or VIP in the format ``http[s]://<host>[:port]``.
* Add a mapping for each JSON file in the ``./schemas`` sub folder of your choice via:
  (NOTE: It's important to do this BEFORE starting a TSD that would index data as you can't modify the mappings for documents that have already been indexed [afaik])

```  
  curl -X PUT -d @schemas/opentsdb_index.json http://<eshost>/opentsdb/
```

* Optionally add ``tsd.core.meta.enable_tracking = true`` to your TSD config if it's processing incoming data
* Turn up the TSD OR...
* ... if you have existing data, run the ``uid metasync`` utility from OpenTSDB

## Schemas

* Add the following configs to your ``opentsdb.conf``file.
  * Add ``tsd.search.elasticsearch.index = opentsdb`` the index of documents
  * Add ``tsd.search.elasticsearch.type = meta``  the type of documents
  * Add ``tsd.search.elasticsearch.uidmeta_type = uidmeta`` the prefix string of the document id of uidmeta 
  * Add ``tsd.search.elasticsearch.tsmeta_type = tsmeta`` the prefix string of the document id of tsmeta
  * Add ``tsd.search.elasticsearch.annotation_type = annotation`` the prefix string of the document id of annotation

* The rule of generate document id as follows.
  * uidmeta: document_id = uidmeta+(metric|tagk|tagv)+uid
  * tsmeta: document_id = tsmeta+tsuid
  * annotation: document_id = annotation+ts[+tsuid]
 
## HttpRpcPlugin
* Add ``net.opentsdb.tsd.SearchHttpRpcPlugin`` to transfer the request to ElasticSearch host.
``/plugin/search/index`` transfer the request to ElasticSearch. Support GET or POST Request, The request body and response, please read the EalsticSearch documents.
``/plugin/search/version`` return the plugin version.
TODO - doc em
