class oasis (
  $elasticsearch_host     = 'localhost',
  $elasticsearch_cluster  = 'elasticsearch',
  $package_url            = false,
) {

  if ! defined(Package['openjdk-7-jdk']) {
    package { 'openjdk-7-jdk':
      ensure => present,
    }
  }

  class {'::logstash':
    provider => 'custom',
    jarfile  => download_file('files', 'logstash/logstash-1.2.2-flatjar.jar', 'https://download.elasticsearch.org/logstash/logstash/logstash-1.2.2-flatjar.jar'),
  }
  logstash::input::tcp {'tcp_input':
    type            => 'oasis',
    port            => 11111,
    format          => 'json',
  }

  logstash::output::elasticsearch {'es_output':
    cluster         => $elasticsearch_cluster,
  }

  if $package_url {
    # create a local copy of the package
    $filename_array = split($package_url, '/')
    $base_filename = $filename_array[-1]
    $tmp_source = "/tmp/${base_filename}"

    file { $tmp_source:
      source => $package_url,
      owner  => 'root',
      group  => 'root',
      backup => false,
    }

    package { 'oasis':
      ensure   => latest,
      provider => 'dpkg',
      source   => $tmp_source,
      require  => [Package['openjdk-7-jdk'], File[$tmp_source]],
      notify   => Service['oasis'],
    }

    service {'oasis':
      ensure => running,
      enable => true,
    }
  }
}

