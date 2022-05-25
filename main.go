package main

import (
	"fmt"
	"github.com/shigeki/floc_simulator/packages/floc"
	"log"
)

//
// floc_simulator is caluculate CohortId with using host lists and SortingLshClusters.
// This needs a json file of host list for history data.
//
var kMaxNumberOfBitsInFloc uint8 = 50

func main() {
	var domain_list []string
	domain_list, sorting_lsh_cluster_data, err := floc.SetUp()
	if err != nil {
		log.Fatal(err)
	}
	sim_hash := floc.SimHashString(domain_list, kMaxNumberOfBitsInFloc)
	fmt.Println(sim_hash)
	_ = sorting_lsh_cluster_data
}
